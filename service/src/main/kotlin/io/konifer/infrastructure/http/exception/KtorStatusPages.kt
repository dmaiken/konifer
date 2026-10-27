package io.konifer.infrastructure.http.exception

import io.konifer.common.http.ErrorCode
import io.konifer.common.http.ErrorResponse
import io.konifer.domain.asset.AssetDataTooLargeException
import io.konifer.domain.asset.AssetNotFoundException
import io.konifer.domain.asset.AssetRejectedException
import io.konifer.domain.asset.InvalidAssetException
import io.konifer.domain.context.ContentTypeNotPermittedException
import io.konifer.domain.context.IllegalRequestedTransformationException
import io.konifer.domain.context.InvalidLabelsException
import io.konifer.domain.context.InvalidPathException
import io.konifer.domain.context.InvalidRequestException
import io.konifer.domain.ports.AssetSourceForbiddenException
import io.konifer.domain.ports.AssetSourceTimeoutException
import io.konifer.domain.ports.AssetSourceUnavailableException
import io.konifer.domain.ports.InvalidAssetSourceException
import io.konifer.domain.ports.RemoteAssetTooLargeException
import io.konifer.domain.rules.InvalidRuleDefinitionException
import io.konifer.domain.transformation.InvalidTransformationException
import io.ktor.http.BadContentTypeFormatException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.PayloadTooLargeException
import io.ktor.server.plugins.UnsupportedMediaTypeException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.statuspages.StatusPagesConfig
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.util.logging.KtorSimpleLogger
import kotlin.coroutines.cancellation.CancellationException

private val logger = KtorSimpleLogger("io.konifer.infrastructure.http.exception.StatusPages")

fun Application.configureStatusPages() =
    install(StatusPages) {
        error<InvalidRequestException>(HttpStatusCode.BadRequest, ErrorCode.INVALID_REQUEST)
        error<BadRequestException>(HttpStatusCode.BadRequest, ErrorCode.INVALID_REQUEST)
        error<BadContentTypeFormatException>(HttpStatusCode.BadRequest, ErrorCode.INVALID_REQUEST)
        error<UnsupportedMediaTypeException>(HttpStatusCode.UnsupportedMediaType, ErrorCode.UNSUPPORTED_MEDIA_TYPE)
        error<InvalidAssetException>(HttpStatusCode.BadRequest, ErrorCode.INVALID_REQUEST)
        error<InvalidRuleDefinitionException>(HttpStatusCode.BadRequest, ErrorCode.INVALID_REQUEST)
        error<InvalidLabelsException>(HttpStatusCode.BadRequest, ErrorCode.INVALID_REQUEST)
        error<InvalidPathException>(HttpStatusCode.BadRequest, ErrorCode.INVALID_PATH)
        error<ContentTypeNotPermittedException>(HttpStatusCode.Forbidden, ErrorCode.FORBIDDEN)
        error<AssetNotFoundException>(HttpStatusCode.NotFound, ErrorCode.NOT_FOUND)
        error<IllegalRequestedTransformationException>(HttpStatusCode.BadRequest, ErrorCode.TRANSFORMATION_NOT_ALLOWED)
        error<AssetRejectedException>(HttpStatusCode.BadRequest, ErrorCode.ASSET_REJECTED)
        error<InvalidTransformationException>(HttpStatusCode.BadRequest, ErrorCode.INVALID_TRANSFORMATION, "Invalid transformation")
        error<AssetDataTooLargeException>(HttpStatusCode.BadRequest, ErrorCode.ASSET_TOO_LARGE)
        error<PayloadTooLargeException>(HttpStatusCode.PayloadTooLarge, ErrorCode.ASSET_TOO_LARGE, "Payload too large")
        error<RemoteAssetTooLargeException>(HttpStatusCode.UnprocessableEntity, ErrorCode.ASSET_TOO_LARGE, "Remote asset too large")
        error<InvalidAssetSourceException>(HttpStatusCode.UnprocessableEntity, ErrorCode.INVALID_SOURCE)
        error<AssetSourceForbiddenException>(HttpStatusCode.Forbidden, ErrorCode.FORBIDDEN)
        error<AssetSourceUnavailableException>(HttpStatusCode.BadGateway, ErrorCode.SOURCE_UNAVAILABLE)
        error<AssetSourceTimeoutException>(HttpStatusCode.GatewayTimeout, ErrorCode.SOURCE_UNAVAILABLE, "Timed out retrieving asset source")

        status(HttpStatusCode.NotFound) { call, status ->
            val path = call.request.path()
            if (path == "/assets" || path.startsWith("/assets/") || path == "/rule-evaluations" || path.startsWith("/rule-evaluations/")) {
                call.respond(status, ErrorResponse("Not found", ErrorCode.NOT_FOUND))
            }
        }
        exception<Exception> { call, cause ->
            if (cause is CancellationException) throw cause
            logger.error("Unexpected failure for ${call.request.path()}", cause)
            if (!call.response.isCommitted) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Internal server error", ErrorCode.INTERNAL_ERROR))
            }
        }
    }

private inline fun <reified E : Throwable> StatusPagesConfig.error(
    status: HttpStatusCode,
    code: ErrorCode,
    message: String? = null,
) {
    exception<E> { call, cause ->
        logger.info("Returning $status for ${call.request.path()}", cause)
        if (!call.response.isCommitted) {
            call.respond(status, ErrorResponse(message ?: cause.message, code))
        }
    }
}
