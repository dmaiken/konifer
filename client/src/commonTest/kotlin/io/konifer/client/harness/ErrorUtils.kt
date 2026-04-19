package io.konifer.client.harness

import io.konifer.common.http.ErrorResponse
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.serialization.json.Json

fun createErrorResponse(message: String) =
    ErrorResponse(
        message = message,
    )

fun configureMockEngineError(
    expectedPath: String,
    response: ErrorResponse,
    statusCode: HttpStatusCode = HttpStatusCode.NotFound,
): MockEngine =
    MockEngine { request ->
        request.url.encodedPath shouldBe expectedPath

        respond(
            content = Json.encodeToString(response),
            status = statusCode,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }
