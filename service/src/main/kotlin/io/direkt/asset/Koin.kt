package io.direkt.asset

import io.direkt.service.context.RequestContextFactory
import io.direkt.infrastructure.asset.AssetStreamContainerFactory
import io.direkt.asset.handler.DeleteAssetHandler
import io.direkt.asset.handler.FetchAssetHandler
import io.direkt.service.transformation.TransformationNormalizer
import io.direkt.asset.handler.UpdateAssetHandler
import io.direkt.domain.ports.AssetRepository
import io.direkt.infrastructure.inmemory.InMemoryAssetRepository
import io.direkt.infrastructure.postgres.PostgresAssetRepository
import io.direkt.asset.variant.VariantProfileRepository
import io.direkt.domain.ports.AssetContainerFactory
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

        single<AssetContainerFactory> {
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
