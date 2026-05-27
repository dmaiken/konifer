package io.konifer.infrastructure

import io.konifer.application.listener.AssetEventListener
import io.konifer.application.service.VariantProcessorPipeline
import io.konifer.application.usecase.delete.DeleteAssetUseCase
import io.konifer.application.usecase.fetch.FetchAssetHandler
import io.konifer.application.usecase.store.StoreNewAssetUseCase
import io.konifer.application.usecase.update.UpdateAssetUseCase
import io.konifer.domain.asset.FormatValidator
import io.konifer.domain.context.RequestContextFactory
import io.konifer.domain.ports.EventBus
import io.konifer.domain.ports.EventPublisher
import io.konifer.domain.transformation.TransformationNormalizer
import io.konifer.domain.variant.VariantService
import io.konifer.infrastructure.asset.assetContainerFactoryModule
import io.konifer.infrastructure.datastore.assetRepositoryModule
import io.konifer.infrastructure.e.InMemoryEventBus
import io.konifer.infrastructure.http.httpClientModule
import io.konifer.infrastructure.http.httpModule
import io.konifer.infrastructure.objectstore.ObjectStoreProvider
import io.konifer.infrastructure.objectstore.objectStoreModule
import io.konifer.infrastructure.path.pathModule
import io.konifer.infrastructure.tika.mimeTypeDetectorModule
import io.konifer.infrastructure.variant.variantModule
import io.konifer.infrastructure.vips.vipsModule
import io.ktor.server.application.Application
import io.ktor.server.application.install
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun Application.configureKoin(
    objectStoreProvider: ObjectStoreProvider,
    additionalModules: List<Module> = emptyList(),
) {
    install(Koin) {
        slf4jLogger()
        modules(
            httpClientModule(),
            httpModule(),
            domainModule(),
            appModule(),
            assetContainerFactoryModule(),
            mimeTypeDetectorModule(),
            assetRepositoryModule(),
            variantModule(),
            objectStoreModule(objectStoreProvider),
            pathModule(),
            vipsModule(),
            *additionalModules.toTypedArray(),
        )
    }
}

fun domainModule(): Module =
    module {
        single<RequestContextFactory> {
            RequestContextFactory(get(), get(), get())
        }
        single<TransformationNormalizer> {
            TransformationNormalizer(get())
        }
        single<VariantService> {
            VariantService(get(), get(), get(), get())
        }

        val eventBus = InMemoryEventBus()
        single<EventPublisher> {
            eventBus
        }
        single<EventBus> {
            eventBus
        }

        single<FormatValidator> {
            FormatValidator(get())
        }
    }

fun appModule(): Module =
    module {
        single<FetchAssetHandler> {
            FetchAssetHandler(get(), get(), get(), get())
        }
        single<DeleteAssetUseCase> {
            DeleteAssetUseCase(get())
        }
        single<UpdateAssetUseCase> {
            UpdateAssetUseCase(get())
        }
        single<StoreNewAssetUseCase> {
            StoreNewAssetUseCase(get(), get(), get(), get(), get(), get(), get())
        }
        single<VariantProcessorPipeline> {
            VariantProcessorPipeline(get(), get())
        }
        single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
        single<AssetEventListener>(createdAtStart = true) {
            AssetEventListener(get(), get(), get(), get())
        }
    }
