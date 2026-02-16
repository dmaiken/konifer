package io.konifer

import io.konifer.infrastructure.configureKoin
import io.konifer.infrastructure.http.cache.configureConditionalHeaders
import io.konifer.infrastructure.http.configureCompression
import io.konifer.infrastructure.http.exception.configureStatusPages
import io.konifer.infrastructure.http.route.configureAssetRouting
import io.konifer.infrastructure.http.route.configureInMemoryObjectStoreRouting
import io.konifer.infrastructure.http.serialization.configureContentNegotiation
import io.konifer.infrastructure.http.signature.configureSignatureVerification
import io.konifer.infrastructure.objectstore.ObjectStoreProvider
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.OBJECT_STORE
import io.konifer.infrastructure.tryGetConfig
import io.ktor.server.application.Application
import io.ktor.server.config.tryGetString
import io.ktor.server.netty.EngineMain
import io.ktor.util.logging.KtorSimpleLogger
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.ObjectRepositoryPropertyKeys.PROVIDER as OBJECT_STORE_PROVIDER

private val logger = KtorSimpleLogger("io.konifer.Application")

/**
 * Before you think about configuring this using embeddedServer, think again. This will break the ability to use
 * eternalized config through ktor (-config) and you will have to mount any externalized config manually!
 */
fun main(args: Array<String>) {
    logger.info("Starting Konifer...")
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
