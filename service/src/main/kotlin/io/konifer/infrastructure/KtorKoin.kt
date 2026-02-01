package io.konifer.infrastructure

import io.konifer.domain.workflow.DeleteAssetWorkflow
import io.konifer.domain.workflow.FetchAssetHandler
import io.konifer.domain.workflow.StoreNewAssetWorkflow
import io.konifer.domain.workflow.UpdateAssetWorkflow
import io.konifer.infrastructure.asset.assetContainerFactoryModule
import io.konifer.infrastructure.datastore.assetRepositoryModule
import io.konifer.infrastructure.http.httpClientModule
import io.konifer.infrastructure.http.httpModule
import io.konifer.infrastructure.objectstore.ObjectStoreProvider
import io.konifer.infrastructure.objectstore.objectStoreModule
import io.konifer.infrastructure.path.pathModule
import io.konifer.infrastructure.tika.mimeTypeDetectorModule
import io.konifer.infrastructure.variant.variantModule
import io.konifer.infrastructure.vips.vipsModule
import io.konifer.service.context.RequestContextFactory
import io.konifer.service.transformation.TransformationNormalizer
import io.konifer.service.variant.VariantService
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
            FetchAssetHandler(get(), get(), get(), get())
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
