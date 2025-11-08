package io

import io.asset.http.configureAssetRouting
import io.database.connectToPostgres
import io.database.migrateSchema
import io.inmemory.configureInMemoryObjectStoreRouting
import io.ktor.server.application.Application
import io.ktor.server.config.tryGetString
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.logging.KtorSimpleLogger
import io.path.configuration.configurePathConfigurationRouting

private val logger = KtorSimpleLogger("io.Application")

fun main() {
    embeddedServer(
        factory = Netty,
        port = 0,
        module = Application::module,
    ).start(wait = true)
}

fun Application.module() {
    val inMemoryObjectStoreEnabled = environment.config.tryGetString("object-store.in-memory")?.toBoolean() ?: false
    if (environment.config.tryGetString("database.in-memory")?.toBoolean() == true) {
        configureKoin(null, inMemoryObjectStoreEnabled)
    } else {
        val connectionFactory = connectToPostgres()
        migrateSchema(connectionFactory)
        configureKoin(connectionFactory, inMemoryObjectStoreEnabled)
    }
    configureContentNegotiation()
    configureRouting(inMemoryObjectStoreEnabled)
    configureStatusPages()
}

fun Application.configureRouting(inMemoryObjectStore: Boolean) {
    configureAssetRouting()
    configurePathConfigurationRouting()

    if (inMemoryObjectStore) {
        logger.info("Configuring in-memory object store APIs. These should only be enabled during testing!!")
        configureInMemoryObjectStoreRouting()
    }
}
