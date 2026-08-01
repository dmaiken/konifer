package io.konifer.entrypoint

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.util.logging.KtorSimpleLogger
import java.util.concurrent.atomic.AtomicBoolean

private val logger = KtorSimpleLogger("io.konifer.entrypoint.HealthRoutes")

fun Application.configureHealthRouting() {
    logger.info("Configuring health routes")
    val isReady = AtomicBoolean(false)

    monitor.subscribe(ApplicationStarted) {
        isReady.set(true)
    }

    monitor.subscribe(ApplicationStopping) {
        isReady.set(false)
    }

    routing {
        route("/health") {
            get {
                if (isReady.get()) {
                    call.respond(
                        status = HttpStatusCode.OK,
                        message = mapOf("status" to "okay"),
                    )
                } else {
                    call.respond(
                        status = HttpStatusCode.ServiceUnavailable,
                        message = mapOf("status" to "not_okay"),
                    )
                }
            }
        }
    }
}
