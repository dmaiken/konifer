package io.path.configuration

import io.ktor.server.application.Application
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.util.logging.KtorSimpleLogger

private val logger = KtorSimpleLogger("io.path.configuration")

fun Application.configurePathConfigurationRouting() {
    logger.info("Configuring path configuration routing...")

    routing {
        get("/pathConfiguration/{...}") {
        }
    }
}
