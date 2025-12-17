package io.direkt.infrastructure

import io.direkt.domain.workflow.DeleteAssetWorkflow
import io.direkt.domain.workflow.FetchAssetHandler
import io.direkt.domain.workflow.StoreNewAssetWorkflow
import io.direkt.domain.workflow.UpdateAssetWorkflow
import io.direkt.infrastructure.asset.assetContainerFactoryModule
import io.direkt.infrastructure.datastore.assetRepositoryModule
import io.direkt.infrastructure.http.httpClientModule
import io.direkt.infrastructure.http.httpModule
import io.direkt.infrastructure.objectstore.ObjectStoreProvider
import io.direkt.infrastructure.objectstore.objectStoreModule
import io.direkt.infrastructure.path.pathModule
import io.direkt.infrastructure.tika.mimeTypeDetectorModule
import io.direkt.infrastructure.variant.variantModule
import io.direkt.infrastructure.vips.vipsModule
import io.direkt.service.context.RequestContextFactory
import io.direkt.service.transformation.TransformationNormalizer
import io.direkt.service.variant.VariantService
import io.ktor.server.application.Application
import io.ktor.server.application.install
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun Application.configureKoin(objectStoreProvider: ObjectStoreProvider) {
    install(Koin) {
        slf4jLogger()
        modules(
            httpClientModule(),
            httpModule(),
            domainModule(),
            assetContainerFactoryModule(),
            mimeTypeDetectorModule(),
            assetRepositoryModule(),
            variantModule(),
            objectStoreModule(objectStoreProvider),
            pathModule(),
            vipsModule(),
        )
    }
}

fun domainModule(): Module =
    module {
        single<StoreNewAssetWorkflow> {
            StoreNewAssetWorkflow(get(), get(), get(), get(), get(), get(), get(), get(), get())
        }
        single<FetchAssetHandler> {
            FetchAssetHandler(get(), get(), get())
        }
        single<DeleteAssetWorkflow> {
            DeleteAssetWorkflow(get())
        }
        single<UpdateAssetWorkflow> {
            UpdateAssetWorkflow(get())
        }

        single<RequestContextFactory> {
            RequestContextFactory(get(), get(), get())
        }

        single<TransformationNormalizer> {
            TransformationNormalizer(get())
        }

        single<VariantService> {
            VariantService(get(), get(), get(), get())
        }
    }
