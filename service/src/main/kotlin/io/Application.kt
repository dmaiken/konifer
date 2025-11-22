package io

import io.asset.http.configureAssetRouting
import io.database.connectToPostgres
import io.database.migrateSchema
import io.inmemory.configureInMemoryObjectStoreRouting
import io.ktor.server.application.Application
import io.ktor.server.config.tryGetString
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.EngineMain
import io.ktor.server.netty.Netty
import io.ktor.util.logging.KtorSimpleLogger
import io.path.configuration.configurePathConfigurationRouting

private val logger = KtorSimpleLogger("io.Application")

/**
 * Before you think about configuring this using embeddedServer, think again. This will break the ability to use
 * eternalized config through ktor (-config) and you will have to mount any externalized config manually!
 */
fun main(args: Array<String>) {
    EngineMain.main(args)
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
