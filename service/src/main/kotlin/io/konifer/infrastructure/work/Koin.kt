package io.konifer.infrastructure.work

import io.konifer.infrastructure.property.ConfigurationPropertyKeys.VARIANT_GENERATION
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.VariantGenerationConfigurationPropertyKeys.QUEUE_SIZE
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.VariantGenerationConfigurationPropertyKeys.SYNCHRONOUS_PRIORITY
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.VariantGenerationConfigurationPropertyKeys.WORKERS
import io.konifer.infrastructure.tryGetConfig
import io.ktor.server.application.Application
import io.ktor.server.config.tryGetString
import kotlinx.coroutines.channels.Channel
import org.koin.core.qualifier.named
import org.koin.dsl.module

val synchronousChannel = named("synchronousChannel")
val backgroundChannel = named("backgroundChannel")

fun Application.workModule() =
    module {
        val queueSize =
            environment.config
                .tryGetConfig(VARIANT_GENERATION)
                ?.tryGetString(QUEUE_SIZE)
                ?.toInt()
                ?: 1000

        single(synchronousChannel) {
            Channel<WorkItem<*>>(capacity = queueSize)
        }

        single(backgroundChannel) {
            Channel<WorkItem<*>>(capacity = queueSize)
        }

        single<PriorityChannelConsumer<WorkItem<*>>> {
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

        single<WorkItemConsumer>(createdAtStart = true) {
            val numberOfWorkers =
                environment.config
                    .tryGetConfig(VARIANT_GENERATION)
                    ?.tryGetString(WORKERS)
                    ?.toInt()
                    ?: Runtime.getRuntime().availableProcessors()
            WorkItemConsumer(
                imageProcessor = get(),
                originalVariantContentService = get(),
                ruleDefinitionEvaluationService = lazy { get() },
                consumer = get(),
                numberOfWorkers = numberOfWorkers,
            )
        }
    }
