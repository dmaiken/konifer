package io.konifer.infrastructure.variant

import io.konifer.domain.ports.VariantGenerator
import io.konifer.domain.ports.VariantProfileRepository
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.VARIANT_GENERATION
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.VariantGenerationConfigurationPropertyKeys.QUEUE_SIZE
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.VariantGenerationConfigurationPropertyKeys.SYNCHRONOUS_PRIORITY
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.VariantGenerationConfigurationPropertyKeys.WORKERS
import io.konifer.infrastructure.tryGetConfig
import io.konifer.infrastructure.variant.profile.ConfigurationVariantProfileRepository
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
            Channel<ImageProcessingJob<*>>(capacity = queueSize)
        }

        single(named("backgroundChannel")) {
            Channel<ImageProcessingJob<*>>(capacity = queueSize)
        }

        single<CoroutineVariantGenerator>(createdAtStart = true) {
            val numberOfWorkers =
                environment.config
                    .tryGetConfig(VARIANT_GENERATION)
                    ?.tryGetString(WORKERS)
                    ?.toInt()
                    ?: Runtime.getRuntime().availableProcessors()
            CoroutineVariantGenerator(get(), get(), numberOfWorkers)
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
