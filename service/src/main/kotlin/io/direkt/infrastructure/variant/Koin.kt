package io.direkt.infrastructure.variant

import io.direkt.domain.ports.VariantGenerator
import io.direkt.domain.ports.VariantProfileRepository
import io.direkt.infrastructure.properties.ConfigurationProperties.VARIANT_GENERATION
import io.direkt.infrastructure.properties.ConfigurationProperties.VariantGenerationConfigurationProperties.QUEUE_SIZE
import io.direkt.infrastructure.properties.ConfigurationProperties.VariantGenerationConfigurationProperties.SYNCHRONOUS_PRIORITY
import io.direkt.infrastructure.properties.ConfigurationProperties.VariantGenerationConfigurationProperties.WORKERS
import io.direkt.infrastructure.tryGetConfig
import io.direkt.infrastructure.variant.profile.ConfigurationVariantProfileRepository
import io.ktor.server.application.Application
import io.ktor.server.config.tryGetString
import kotlinx.coroutines.channels.Channel
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun Application.variantModule(): Module =
    module {
        val queueSize =
            environment.config
                .tryGetConfig(VARIANT_GENERATION)
                ?.tryGetString(QUEUE_SIZE)
                ?.toInt()
                ?: 1000
        single(named("synchronousChannel")) {
            Channel<OnDemandVariantGenerationJob>(capacity = queueSize)
        }

        single(named("backgroundChannel")) {
            Channel<OnDemandVariantGenerationJob>(capacity = queueSize)
        }

        single<CoroutineVariantGenerator>(createdAtStart = true) {
            val numberOfWorkers =
                environment.config
                    .tryGetConfig(VARIANT_GENERATION)
                    ?.tryGetString(WORKERS)
                    ?.toInt()
                    ?: Runtime.getRuntime().availableProcessors()
            CoroutineVariantGenerator(get(), get(), get(), get(), get(), numberOfWorkers)
        }

        single<PriorityChannelConsumer<ImageProcessingJob<*>>> {
            val synchronousWeight =
                environment.config
                    .tryGetConfig(VARIANT_GENERATION)
                    ?.tryGetString(SYNCHRONOUS_PRIORITY)
                    ?.toInt()
                    ?: 80
            PriorityChannelConsumer(
                highPriorityChannel = get(named("synchronousChannel")),
                backgroundChannel = get(named("backgroundChannel")),
                highPriorityWeight = synchronousWeight,
            )
        }

        single<VariantGenerator> {
            PrioritizedChannelVariantScheduler(
                get(named("synchronousChannel")),
                get(named("backgroundChannel")),
            )
        }

        single<VariantProfileRepository> {
            ConfigurationVariantProfileRepository(environment.config)
        }
    }
