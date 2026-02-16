package io.konifer.infrastructure.http.exception

import io.konifer.domain.asset.AssetNotFoundException
import io.konifer.domain.image.InvalidImageException
import io.konifer.service.context.ContentTypeNotPermittedException
import io.konifer.service.context.InvalidPathException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.util.logging.KtorSimpleLogger

private val logger = KtorSimpleLogger("io.konifer.infrastructure.http.StatusPages")

fun Application.configureStatusPages() =
    install(StatusPages) {
        exception<InvalidImageException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message))
        }
        exception<IllegalArgumentException> { call, cause ->
            logger.info("Returning ${HttpStatusCode.BadRequest} for ${call.request.path()}", cause)
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message))
        }
        exception<InvalidPathException> { call, cause ->
            logger.info("Returning ${HttpStatusCode.BadRequest} for ${call.request.path()}", cause)
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message))
        }
        exception<ContentTypeNotPermittedException> { call, cause ->
            logger.info("Returning ${HttpStatusCode.Forbidden} for ${call.request.path()}", cause)
            call.respond(HttpStatusCode.Forbidden, ErrorResponse(cause.message))
        }
        exception<AssetNotFoundException> { call, cause ->
            logger.info("Returning ${HttpStatusCode.NotFound} for ${call.request.path()}", cause)
            call.respond(HttpStatusCode.NotFound, ErrorResponse(cause.message))
        }
    }
