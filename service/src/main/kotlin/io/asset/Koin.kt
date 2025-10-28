package io.asset

import io.asset.context.RequestContextFactory
import io.asset.handler.AssetStreamContainerFactory
import io.asset.handler.DeleteAssetHandler
import io.asset.handler.FetchAssetHandler
import io.asset.handler.StoreAssetHandler
import io.asset.handler.TransformationNormalizer
import io.asset.repository.AssetRepository
import io.asset.repository.InMemoryAssetRepository
import io.asset.repository.PostgresAssetRepository
import io.asset.variant.VariantProfileRepository
import io.asset.variant.generation.ImageProcessingJob
import io.asset.variant.generation.PriorityChannelScheduler
import io.asset.variant.generation.VariantGenerationJob
import io.asset.variant.generation.VariantGenerator
import io.ktor.server.application.Application
import io.ktor.server.config.tryGetString
import io.ktor.server.config.tryGetStringList
import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.channels.Channel
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun Application.assetModule(connectionFactory: ConnectionFactory?): Module =
    module {
        single<StoreAssetHandler> {
            StoreAssetHandler(get(), get(), get(), get(), get(), get(), get())
        }
        single<FetchAssetHandler> {
            FetchAssetHandler(get(), get(), get())
        }
        single<DeleteAssetHandler> {
            DeleteAssetHandler(get())
        }
        single<MimeTypeDetector> {
            TikaMimeTypeDetector()
        }

        single<AssetStreamContainerFactory> {
            val allowedDomains = environment.config.tryGetStringList("source.url.allowed-domains")?.toSet() ?: emptySet()
            val maxContentLength = environment.config.tryGetString("source.url.max-bytes")?.toLong() ?: MAX_BYTES_DEFAULT

            AssetStreamContainerFactory(allowedDomains, maxContentLength, get())
        }

        single<AssetRepository> {
            connectionFactory?.let {
                PostgresAssetRepository(get())
            } ?: InMemoryAssetRepository()
        }

        single<RequestContextFactory> {
            RequestContextFactory(get(), get(), get())
        }

        single<VariantProfileRepository> {
            VariantProfileRepository(environment.config)
        }

        single(named("synchronousChannel")) {
            val queueSize =
                environment.config.propertyOrNull("variant-generation.queue-size")?.getString()?.toInt()
                    ?: 1000
            Channel<VariantGenerationJob>(capacity = queueSize)
        }

        single(named("backgroundChannel")) {
            val queueSize =
                environment.config.propertyOrNull("variant-generation.queue-size")?.getString()?.toInt()
                    ?: 1000
            Channel<VariantGenerationJob>(capacity = queueSize)
        }

        single<VariantGenerator>(createdAtStart = true) {
            val numberOfWorkers =
                environment.config.propertyOrNull("variant-generation.workers")?.getString()?.toInt()
                    ?: Runtime.getRuntime().availableProcessors()
            VariantGenerator(get(), get(), get(), get(), get(), numberOfWorkers)
        }

        single<PriorityChannelScheduler<ImageProcessingJob<*>>> {
            val synchronousWeight = environment.config.propertyOrNull("variant-generation.synchronous-priority")?.getString()?.toInt() ?: 80
            PriorityChannelScheduler(
                get(named("synchronousChannel")),
                get(named("backgroundChannel")),
                synchronousWeight,
            )
        }

        single<TransformationNormalizer> {
            TransformationNormalizer(get())
        }
    }
