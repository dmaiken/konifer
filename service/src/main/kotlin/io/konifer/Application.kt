package io.konifer

import io.konifer.infrastructure.configureKoin
import io.konifer.infrastructure.getObjectStoreProvider
import io.konifer.infrastructure.http.cache.configureConditionalHeaders
import io.konifer.infrastructure.http.configureCompression
import io.konifer.infrastructure.http.exception.configureStatusPages
import io.konifer.infrastructure.http.route.configureAssetRouting
import io.konifer.infrastructure.http.route.configureInMemoryObjectStoreRouting
import io.konifer.infrastructure.http.serialization.configureContentNegotiation
import io.konifer.infrastructure.http.signature.configureSignatureVerification
import io.konifer.infrastructure.objectstore.ObjectStoreProvider
import io.ktor.server.application.Application
import io.ktor.server.netty.EngineMain
import io.ktor.util.logging.KtorSimpleLogger

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
    val objectStoreProvider = environment.config.getObjectStoreProvider()

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
        configureInMemoryObjectStoreRouting()
    }
}
