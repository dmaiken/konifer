package io.konifer

import io.konifer.entrypoint.configureAssetRouting
import io.konifer.entrypoint.configureHealthRouting
import io.konifer.entrypoint.configureInMemoryObjectStoreRouting
import io.konifer.infrastructure.configureKoin
import io.konifer.infrastructure.getObjectStoreProvider
import io.konifer.infrastructure.http.cache.configureConditionalHeaders
import io.konifer.infrastructure.http.configureCompression
import io.konifer.infrastructure.http.exception.configureStatusPages
import io.konifer.infrastructure.http.serialization.configureContentNegotiation
import io.konifer.infrastructure.http.signature.configureSignatureVerification
import io.konifer.infrastructure.objectstore.ObjectStoreProvider
import io.ktor.server.application.Application
import io.ktor.server.netty.EngineMain
import io.ktor.util.logging.KtorSimpleLogger
import org.koin.core.module.Module

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
    module(additionalModules = emptyList())
}

fun Application.module(additionalModules: List<Module>) {
    val objectStoreProvider = environment.config.getObjectStoreProvider()

    configureKoin(objectStoreProvider, additionalModules)
    configureContentNegotiation()
    configureRouting(objectStoreProvider)
    configureStatusPages()
    configureSignatureVerification()
    configureConditionalHeaders()
    configureCompression()
}

fun Application.configureRouting(objectStoreProvider: ObjectStoreProvider) {
    configureAssetRouting()
    configureHealthRouting()

    if (objectStoreProvider == ObjectStoreProvider.IN_MEMORY) {
        configureInMemoryObjectStoreRouting()
    }
}
