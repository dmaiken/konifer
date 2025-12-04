package io.direkt.infrastructure.variant

import io.direkt.domain.ports.VariantGenerator
import io.ktor.server.application.Application
import kotlinx.coroutines.channels.Channel
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun Application.variantModule(): Module =
    module {
        single(named("synchronousChannel")) {
            val queueSize =
                environment.config
                    .propertyOrNull("variant-generation.queue-size")
                    ?.getString()
                    ?.toInt()
                    ?: 1000
            Channel<OnDemandVariantGenerationJob>(capacity = queueSize)
        }

        single(named("backgroundChannel")) {
            val queueSize =
                environment.config
                    .propertyOrNull("variant-generation.queue-size")
                    ?.getString()
                    ?.toInt()
                    ?: 1000
            Channel<OnDemandVariantGenerationJob>(capacity = queueSize)
        }

        single<CoroutineVariantGenerator>(createdAtStart = true) {
            val numberOfWorkers =
                environment.config
                    .propertyOrNull("variant-generation.workers")
                    ?.getString()
                    ?.toInt()
                    ?: Runtime.getRuntime().availableProcessors()
            CoroutineVariantGenerator(get(), get(), get(), get(), get(), numberOfWorkers)
        }

        single<PriorityChannelConsumer<ImageProcessingJob<*>>> {
            val synchronousWeight =
                environment.config
                    .propertyOrNull("variant-generation.synchronous-priority")
                    ?.getString()
                    ?.toInt() ?: 80
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
    }
