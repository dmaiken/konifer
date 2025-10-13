package io.asset

import io.asset.context.RequestContextFactory
import io.asset.handler.AssetHandler
import io.asset.handler.RequestedTransformationNormalizer
import io.asset.repository.AssetRepository
import io.asset.repository.InMemoryAssetRepository
import io.asset.repository.PostgresAssetRepository
import io.asset.variant.VariantGenerationJob
import io.asset.variant.VariantGenerator
import io.asset.variant.VariantParameterGenerator
import io.asset.variant.VariantProfileRepository
import io.ktor.server.application.Application
import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.channels.Channel
import org.koin.core.module.Module
import org.koin.dsl.module

fun Application.assetModule(connectionFactory: ConnectionFactory?): Module =
    module {
        single<AssetHandler> {
            AssetHandler(get(), get(), get(), get(), get(), get(), get(), get())
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
            RequestContextFactory(get(), get(), get())
        }

        single<VariantProfileRepository> {
            VariantProfileRepository(environment.config)
        }

        single { Channel<VariantGenerationJob>() }

        single<VariantGenerator> {
            VariantGenerator(get(), get(), get(), get(), get())
        }

        single<RequestedTransformationNormalizer> {
            RequestedTransformationNormalizer(get())
        }
    }
