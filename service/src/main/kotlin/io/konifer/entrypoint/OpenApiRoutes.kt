package io.konifer.entrypoint

import io.ktor.http.ContentType
import io.ktor.openapi.OpenApiInfo
import io.ktor.server.application.Application
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.routing.openapi.OpenApiDocSource
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

fun Application.configureOpenApiRoutes() {
    routing {
        route("api") {
            swaggerUI("docs") {
                info = OpenApiInfo("Konifer API", "1.0.0")
                remotePath = "openapi.json"
                source =
                    OpenApiDocSource.Routing(
                        contentType = ContentType.Application.Json,
                    )
            }
        }
    }
}
