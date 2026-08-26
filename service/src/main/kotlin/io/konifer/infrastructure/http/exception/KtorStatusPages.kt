package io.konifer.infrastructure.http.exception

import io.konifer.common.http.ErrorResponse
import io.konifer.domain.asset.AssetNotFoundException
import io.konifer.domain.asset.AssetRejectedException
import io.konifer.domain.context.ContentTypeNotPermittedException
import io.konifer.domain.context.IllegalRequestedTransformationException
import io.konifer.domain.context.InvalidPathException
import io.konifer.domain.image.InvalidImageException
import io.konifer.domain.ports.AssetSourceForbiddenException
import io.konifer.domain.ports.AssetSourceTimeoutException
import io.konifer.domain.ports.AssetSourceUnavailableException
import io.konifer.domain.ports.InvalidAssetSourceException
import io.konifer.domain.ports.RemoteAssetTooLargeException
import io.konifer.domain.transformation.InvalidTransformationException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.PayloadTooLargeException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.util.logging.KtorSimpleLogger

private val logger = KtorSimpleLogger("io.konifer.infrastructure.http.exception.StatusPages")

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
        exception<IllegalRequestedTransformationException> { call, cause ->
            logger.info("Returning ${HttpStatusCode.BadRequest} for ${call.request.path()}", cause)
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message))
        }
        exception<AssetRejectedException> { call, cause ->
            logger.info("Returning ${HttpStatusCode.BadRequest} for ${call.request.path()}", cause)
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message))
        }
        exception<InvalidTransformationException> { call, cause ->
            logger.info("Returning ${HttpStatusCode.BadRequest} for ${call.request.path()}", cause)
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid transformation"))
        }
        exception<PayloadTooLargeException> { call, cause ->
            logger.info("Returning ${HttpStatusCode.PayloadTooLarge} for ${call.request.path()}", cause)
            call.respond(HttpStatusCode.PayloadTooLarge, ErrorResponse("Payload too large"))
        }
        exception<RemoteAssetTooLargeException> { call, cause ->
            logger.info("Returning ${HttpStatusCode.UnprocessableEntity} for ${call.request.path()}", cause)
            call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("Remote asset too large"))
        }
        exception<InvalidAssetSourceException> { call, cause ->
            logger.info("Returning ${HttpStatusCode.UnprocessableEntity} for ${call.request.path()}", cause)
            call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(cause.message))
        }
        exception<AssetSourceForbiddenException> { call, cause ->
            logger.info("Returning ${HttpStatusCode.Forbidden} for ${call.request.path()}", cause)
            call.respond(HttpStatusCode.Forbidden, ErrorResponse(cause.message))
        }
        exception<AssetSourceUnavailableException> { call, cause ->
            logger.info("Returning ${HttpStatusCode.BadGateway} for ${call.request.path()}", cause)
            call.respond(HttpStatusCode.BadGateway, ErrorResponse(cause.message))
        }
        exception<AssetSourceTimeoutException> { call, cause ->
            logger.info("Returning ${HttpStatusCode.GatewayTimeout} for ${call.request.path()}", cause)
            call.respond(HttpStatusCode.GatewayTimeout, ErrorResponse("Timed out retrieving asset source"))
        }
    }
