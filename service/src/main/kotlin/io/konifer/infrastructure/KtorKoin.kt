package io.konifer.infrastructure

import com.typesafe.config.Config
import io.konifer.application.listener.AssetEventListener
import io.konifer.application.service.OriginalVariantProcessorPipeline
import io.konifer.application.usecase.delete.DeleteAssetUseCase
import io.konifer.application.usecase.fetch.FetchAssetHandler
import io.konifer.application.usecase.store.StoreNewAssetUseCase
import io.konifer.application.usecase.update.UpdateAssetUseCase
import io.konifer.domain.asset.FormatValidator
import io.konifer.domain.context.RequestContextFactory
import io.konifer.domain.context.RequestContextValidator
import io.konifer.domain.ports.EventBus
import io.konifer.domain.ports.EventPublisher
import io.konifer.domain.transformation.TransformationNormalizer
import io.konifer.domain.variant.VariantService
import io.konifer.infrastructure.asset.assetContainerFactoryModule
import io.konifer.infrastructure.datastore.assetRepositoryModule
import io.konifer.infrastructure.event.InMemoryEventBus
import io.konifer.infrastructure.http.httpClientModule
import io.konifer.infrastructure.http.httpModule
import io.konifer.infrastructure.inference.inferenceModule
import io.konifer.infrastructure.objectstore.ObjectStoreProvider
import io.konifer.infrastructure.objectstore.objectStoreModule
import io.konifer.infrastructure.path.extractRawHocon
import io.konifer.infrastructure.path.pathModule
import io.konifer.infrastructure.rules.rulesModule
import io.konifer.infrastructure.tika.mimeTypeDetectorModule
import io.konifer.infrastructure.variant.variantModule
import io.konifer.infrastructure.vips.vipsModule
import io.ktor.server.application.Application
import io.ktor.server.application.install
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.createdAtStart
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import org.koin.plugin.module.dsl.single

fun Application.configureKoin(
    objectStoreProvider: ObjectStoreProvider,
    additionalModules: List<Module> = emptyList(),
) {
    install(Koin) {
        slf4jLogger()
        val configuredModules =
            mutableListOf(
                configModule(),
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
                rulesModule(),
            )

        if (environment.config.hasRuleDefinitions()) {
            configuredModules += inferenceModule()
        }

        configuredModules += additionalModules

        modules(configuredModules)
    }
}

fun Application.configModule(): Module =
    module {
        single<Config> {
            environment.config.extractRawHocon()
        }
    }

fun domainModule(): Module =
    module {
        single<RequestContextFactory>()
        single<RequestContextValidator>()
        single<TransformationNormalizer>()
        single<VariantService>()
        singleOf(::InMemoryEventBus) {
            bind<EventPublisher>()
            bind<EventBus>()
        }
        single<FormatValidator>()
    }

fun appModule(): Module =
    module {
        single<FetchAssetHandler>()
        single<DeleteAssetUseCase>()
        single<UpdateAssetUseCase>()
        single<StoreNewAssetUseCase>()
        single<OriginalVariantProcessorPipeline>()
        single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
        single<AssetEventListener>() withOptions {
            createdAtStart()
        }
    }
