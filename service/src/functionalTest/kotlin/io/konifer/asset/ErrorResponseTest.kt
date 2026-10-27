package io.konifer.asset

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
import io.konifer.infrastructure.http.exception.configureStatusPages
import io.konifer.infrastructure.http.serialization.configureContentNegotiation
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.BadContentTypeFormatException
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.PayloadTooLargeException
import io.ktor.server.plugins.UnsupportedMediaTypeException
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.coroutines.cancellation.CancellationException

class ErrorResponseTest {
    companion object {
        @JvmStatic
        fun failures() =
            listOf(
                Arguments.of(InvalidRequestException("request"), 400, ErrorCode.INVALID_REQUEST),
                Arguments.of(BadRequestException("body"), 400, ErrorCode.INVALID_REQUEST),
                Arguments.of(BadContentTypeFormatException("invalid"), 400, ErrorCode.INVALID_REQUEST),
                Arguments.of(UnsupportedMediaTypeException(ContentType.Text.Plain), 415, ErrorCode.UNSUPPORTED_MEDIA_TYPE),
                Arguments.of(InvalidAssetException("image"), 400, ErrorCode.INVALID_REQUEST),
                Arguments.of(InvalidRuleDefinitionException("rule"), 400, ErrorCode.INVALID_REQUEST),
                Arguments.of(InvalidLabelsException("labels"), 400, ErrorCode.INVALID_REQUEST),
                Arguments.of(InvalidPathException("path"), 400, ErrorCode.INVALID_PATH),
                Arguments.of(InvalidTransformationException("width"), 400, ErrorCode.INVALID_TRANSFORMATION),
                Arguments.of(IllegalRequestedTransformationException("profile"), 400, ErrorCode.TRANSFORMATION_NOT_ALLOWED),
                Arguments.of(AssetRejectedException(emptyList()), 400, ErrorCode.ASSET_REJECTED),
                Arguments.of(AssetDataTooLargeException(1), 400, ErrorCode.ASSET_TOO_LARGE),
                Arguments.of(PayloadTooLargeException(1), 413, ErrorCode.ASSET_TOO_LARGE),
                Arguments.of(RemoteAssetTooLargeException(), 422, ErrorCode.ASSET_TOO_LARGE),
                Arguments.of(InvalidAssetSourceException("source"), 422, ErrorCode.INVALID_SOURCE),
                Arguments.of(ContentTypeNotPermittedException("format"), 403, ErrorCode.FORBIDDEN),
                Arguments.of(AssetSourceForbiddenException("host"), 403, ErrorCode.FORBIDDEN),
                Arguments.of(AssetNotFoundException(message = "missing"), 404, ErrorCode.NOT_FOUND),
                Arguments.of(AssetSourceUnavailableException(), 502, ErrorCode.SOURCE_UNAVAILABLE),
                Arguments.of(AssetSourceTimeoutException(), 504, ErrorCode.SOURCE_UNAVAILABLE),
                Arguments.of(IllegalArgumentException("internal secret"), 500, ErrorCode.INTERNAL_ERROR),
                Arguments.of(IllegalStateException("internal secret"), 500, ErrorCode.INTERNAL_ERROR),
            )
    }

    @Test
    fun `server cancellation does not become an application JSON error`() =
        testApplication {
            application {
                configureContentNegotiation()
                configureStatusPages()
                routing { get("/assets/cancel") { throw CancellationException("cancelled") } }
            }
            // The test engine handles propagated cancellation; StatusPages must not write an INTERNAL_ERROR body.
            client.get("/assets/cancel").bodyAsText() shouldNotContain "INTERNAL_ERROR"
        }

    @Test
    fun `unexpected failures do not rewrite committed responses`() =
        testApplication {
            application {
                configureContentNegotiation()
                configureStatusPages()
                routing {
                    get("/assets/committed") {
                        call.respondText("already sent")
                        throw IllegalArgumentException("internal secret")
                    }
                }
            }
            val response = client.get("/assets/committed")
            response.status.value shouldBe 200
            response.bodyAsText() shouldBe "already sent"
        }

    @ParameterizedTest
    @MethodSource("failures")
    fun `exceptions have explicit status and error codes`(
        failure: Exception,
        status: Int,
        code: ErrorCode,
    ) = testApplication {
        application {
            configureContentNegotiation()
            configureStatusPages()
            routing { get("/assets/error") { throw failure } }
        }
        val response = client.get("/assets/error")
        response.status.value shouldBe status
        response.contentType()?.withoutParameters() shouldBe ContentType.Application.Json
        val error = Json { explicitNulls = false }.decodeFromString<ErrorResponse>(response.bodyAsText())
        error.code shouldBe code
        if (code == ErrorCode.INTERNAL_ERROR) error.message shouldBe "Internal server error"
        if (code == ErrorCode.NOT_FOUND) error.message shouldBe "missing"
        if (failure is AssetSourceUnavailableException) error.message shouldBe null
    }
}
