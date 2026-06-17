package io.konifer.infrastructure.variant

import io.konifer.domain.ports.VariantGenerator
import io.konifer.domain.ports.VariantMetricsRepository
import io.konifer.domain.ports.VariantProfileRepository
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.VARIANT_GENERATION
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.VariantGenerationConfigurationPropertyKeys.QUEUE_SIZE
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.VariantGenerationConfigurationPropertyKeys.SYNCHRONOUS_PRIORITY
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.VariantGenerationConfigurationPropertyKeys.WORKERS
import io.konifer.infrastructure.tryGetConfig
import io.konifer.infrastructure.variant.metrics.ChannelVariantMetricsDrainSignal
import io.konifer.infrastructure.variant.metrics.InMemoryVariantMetricsRepository
import io.konifer.infrastructure.variant.metrics.VariantMetricsDrainSignal
import io.konifer.infrastructure.variant.profile.ConfigurationVariantProfileRepository
import io.ktor.server.application.Application
import io.ktor.server.config.tryGetString
import kotlinx.coroutines.channels.Channel
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

fun Application.variantModule(): Module =
    module {
        val synchronousChannel = named("synchronousChannel")
        val backgroundChannel = named("backgroundChannel")
        val queueSize =
            environment.config
                .tryGetConfig(VARIANT_GENERATION)
                ?.tryGetString(QUEUE_SIZE)
                ?.toInt()
                ?: 1000

        single(synchronousChannel) {
            Channel<ImageProcessingJob<*>>(capacity = queueSize)
        }

        single(backgroundChannel) {
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
                highPriorityChannel = get(synchronousChannel),
                backgroundChannel = get(backgroundChannel),
                highPriorityWeight = synchronousWeight,
            )
        }

        single<VariantGenerator> {
            PrioritizedChannelVariantScheduler(
                get(synchronousChannel),
                get(backgroundChannel),
            )
        }

        single<ConfigurationVariantProfileRepository>() bind VariantProfileRepository::class

        single<InMemoryVariantMetricsRepository>() bind VariantMetricsRepository::class
        single<ChannelVariantMetricsDrainSignal>() bind VariantMetricsDrainSignal::class
    }
