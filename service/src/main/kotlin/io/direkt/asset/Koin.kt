package io.direkt.asset

import io.direkt.asset.context.RequestContextFactory
import io.direkt.asset.handler.AssetStreamContainerFactory
import io.direkt.asset.handler.DeleteAssetHandler
import io.direkt.asset.handler.FetchAssetHandler
import io.direkt.asset.handler.TransformationNormalizer
import io.direkt.asset.handler.UpdateAssetHandler
import io.direkt.asset.repository.AssetRepository
import io.direkt.asset.repository.InMemoryAssetRepository
import io.direkt.asset.repository.PostgresAssetRepository
import io.direkt.asset.variant.VariantProfileRepository
import io.direkt.workflows.StoreNewAssetWorkflow
import io.ktor.server.application.Application
import io.ktor.server.config.tryGetString
import io.ktor.server.config.tryGetStringList
import io.r2dbc.spi.ConnectionFactory
import org.koin.core.module.Module
import org.koin.dsl.module

fun Application.assetModule(connectionFactory: ConnectionFactory?): Module =
    module {
        single<StoreNewAssetWorkflow> {
            StoreNewAssetWorkflow(get(), get(), get(), get(), get(), get(), get(), get())
        }
        single<FetchAssetHandler> {
            FetchAssetHandler(get(), get(), get())
        }
        single<DeleteAssetHandler> {
            DeleteAssetHandler(get())
        }
        single<UpdateAssetHandler> {
            UpdateAssetHandler(get())
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

        single<TransformationNormalizer> {
            TransformationNormalizer(get())
        }
    }
