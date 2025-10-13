package io

import asset.configureAssetRouting
import io.database.connectToPostgres
import io.database.migrateSchema
import io.inmemory.configureInMemoryObjectStoreRouting
import io.ktor.server.application.Application
import io.ktor.server.config.tryGetString
import io.ktor.util.logging.KtorSimpleLogger
import io.path.configuration.configurePathConfigurationRouting

private val logger = KtorSimpleLogger("io.Application")

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
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
