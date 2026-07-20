package io.konifer.infrastructure.variant

import io.konifer.domain.ports.OriginalVariantContentProcessor
import io.konifer.domain.ports.VariantGenerator
import io.konifer.domain.ports.VariantMetricsRepository
import io.konifer.domain.ports.VariantProfileRepository
import io.konifer.infrastructure.variant.metrics.ChannelVariantMetricsDrainSignal
import io.konifer.infrastructure.variant.metrics.InMemoryVariantMetricsRepository
import io.konifer.infrastructure.variant.metrics.VariantMetricsDrainSignal
import io.konifer.infrastructure.variant.original.ChannelOriginalVariantContentScheduler
import io.konifer.infrastructure.variant.original.OriginalVariantContentService
import io.konifer.infrastructure.variant.profile.ConfigurationVariantProfileRepository
import io.konifer.infrastructure.work.backgroundChannel
import io.konifer.infrastructure.work.synchronousChannel
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

fun variantModule(): Module =
    module {
        single<OriginalVariantContentService> {
            OriginalVariantContentService(
                ruleEvaluator = lazy { get() },
                vipsTensorProcessor = get(),
                ruleDefinitionRepository = lazy { get() },
                vipsImageProcessor = get(),
            )
        }

        single<OriginalVariantContentProcessor> {
            ChannelOriginalVariantContentScheduler(
                highPriorityChannel = get(synchronousChannel),
            )
        }

        single<VariantGenerator> {
            PrioritizedChannelVariantGenerator(
                get(synchronousChannel),
                get(backgroundChannel),
            )
        }

        single<ConfigurationVariantProfileRepository>() bind VariantProfileRepository::class
        single<InMemoryVariantMetricsRepository>() bind VariantMetricsRepository::class
        single<ChannelVariantMetricsDrainSignal>() bind VariantMetricsDrainSignal::class
    }
