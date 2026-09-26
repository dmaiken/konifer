package io.konifer.entrypoint

import io.konifer.infrastructure.health.KoniferHealthIndicator
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.util.logging.KtorSimpleLogger
import org.koin.ktor.ext.inject

private val logger = KtorSimpleLogger("io.konifer.entrypoint.HealthRoutes")
private val emptyResponse = ByteArray(0)

fun Application.configureHealthRouting() {
    logger.info("Configuring health routes")

    val healthIndicator by inject<KoniferHealthIndicator>()

    routing {
        route("/health") {
            get("live") {
                call.respondProbe(HttpStatusCode.OK)
            }

            get("ready") {
                if (healthIndicator.isHealthy()) {
                    call.respondProbe(HttpStatusCode.OK)
                } else {
                    call.respondProbe(HttpStatusCode.ServiceUnavailable)
                }
            }
        }
    }
}

private suspend fun ApplicationCall.respondProbe(status: HttpStatusCode) {
    respondBytes(
        bytes = emptyResponse,
        status = status,
    )
}
