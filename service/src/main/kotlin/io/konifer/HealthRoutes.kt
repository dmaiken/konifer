package io.konifer

import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

fun Application.configureHealthRouting() {
    routing {
        get("/health") {
            call.respondText("OK")
        }
    }
}
