package io.direkt

import io.direkt.infrastructure.configureKoin
import io.direkt.infrastructure.http.cache.configureConditionalHeaders
import io.direkt.infrastructure.http.configureCompression
import io.direkt.infrastructure.http.configureStatusPages
import io.direkt.infrastructure.http.route.configureAssetRouting
import io.direkt.infrastructure.http.route.configureInMemoryObjectStoreRouting
import io.direkt.infrastructure.http.serialization.configureContentNegotiation
import io.direkt.infrastructure.http.signature.configureSignatureVerification
import io.direkt.infrastructure.objectstore.ObjectStoreProvider
import io.direkt.infrastructure.properties.ConfigurationPropertyKeys.OBJECT_STORE
import io.direkt.infrastructure.tryGetConfig
import io.ktor.server.application.Application
import io.ktor.server.config.tryGetString
import io.ktor.server.netty.EngineMain
import io.ktor.util.logging.KtorSimpleLogger
import io.direkt.infrastructure.properties.ConfigurationPropertyKeys.ObjectRepositoryPropertyKeys.PROVIDER as OBJECT_STORE_PROVIDER

private val logger = KtorSimpleLogger("io.direkt.Application")

/**
 * Before you think about configuring this using embeddedServer, think again. This will break the ability to use
 * eternalized config through ktor (-config) and you will have to mount any externalized config manually!
 */
fun main(args: Array<String>) {
    logger.info("Starting Direkt Netty")
    EngineMain.main(args)
}

fun Application.module() {
    val objectStoreProvider =
        environment.config
            .tryGetConfig(OBJECT_STORE)
            ?.tryGetString(OBJECT_STORE_PROVIDER)
            ?.let {
                ObjectStoreProvider.fromConfig(it)
            } ?: ObjectStoreProvider.default

    configureKoin(objectStoreProvider)
    configureContentNegotiation()
    configureRouting(objectStoreProvider)
    configureStatusPages()
    configureSignatureVerification()
    configureConditionalHeaders()
    configureCompression()
}

fun Application.configureRouting(objectStoreProvider: ObjectStoreProvider) {
    configureAssetRouting()

    if (objectStoreProvider == ObjectStoreProvider.IN_MEMORY) {
        logger.info("Configuring in-memory object store APIs. These should only be enabled during testing!!")
        configureInMemoryObjectStoreRouting()
    }
}
