package asset

import asset.repository.AssetRepository
import asset.repository.InMemoryAssetRepository
import asset.repository.PostgresAssetRepository
import asset.variant.VariantParameterGenerator
import io.asset.context.RequestContextFactory
import io.asset.variant.VariantProfileRepository
import io.ktor.server.application.Application
import io.r2dbc.spi.ConnectionFactory
import org.koin.core.module.Module
import org.koin.dsl.module

fun Application.assetModule(connectionFactory: ConnectionFactory?): Module =
    module {
        single<AssetHandler> {
            AssetHandler(get(), get(), get(), get(), get())
        }
        single<MimeTypeDetector> {
            TikaMimeTypeDetector()
        }

        single<AssetRepository> {
            connectionFactory?.let {
                PostgresAssetRepository(get(), get())
            } ?: InMemoryAssetRepository(get())
        }

        single<VariantParameterGenerator> {
            VariantParameterGenerator()
        }

        single<RequestContextFactory> {
            RequestContextFactory(get(), get())
        }

        single<VariantProfileRepository> {
            VariantProfileRepository(environment.config)
        }
    }
