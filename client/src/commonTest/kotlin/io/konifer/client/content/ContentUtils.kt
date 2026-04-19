package io.konifer.client.content

import io.konifer.client.RequestedTransformation
import io.konifer.client.harness.assertRequestedTransformation
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

const val REDIRECT_URL = "https://cdn.konifer.io/actual-asset.bin"

fun configureMockEngineHappy(
    expectedPath: String,
    bytes: ByteArray,
    mimeType: String = "image/png",
    statusCode: HttpStatusCode = HttpStatusCode.OK,
    requestedTransformation: RequestedTransformation? = null,
): MockEngine =
    MockEngine { request ->
        request.url.encodedPath shouldBe expectedPath
        request.method shouldBe HttpMethod.Get
        assertRequestedTransformation(
            parameters = request.url.parameters,
            requestedTransformation = requestedTransformation,
        )

        respond(
            content = bytes,
            status = statusCode,
            headers = headersOf(HttpHeaders.ContentType, mimeType),
        )
    }

fun configureMockEngineHappyRedirect(
    expectedPath: String,
    bytes: ByteArray,
    mimeType: String = "image/png",
    statusCode: HttpStatusCode = HttpStatusCode.OK,
    requestedTransformation: RequestedTransformation? = null,
): MockEngine =
    MockEngine { request ->
        request.method shouldBe HttpMethod.Get
        val requestUrl = request.url.toString()

        when {
            requestUrl.contains(expectedPath) -> {
                assertRequestedTransformation(
                    parameters = request.url.parameters,
                    requestedTransformation = requestedTransformation,
                )
                respond(
                    content = "",
                    status = HttpStatusCode.TemporaryRedirect,
                    headers = headersOf(HttpHeaders.Location, REDIRECT_URL),
                )
            }
            requestUrl == REDIRECT_URL -> {
                respond(
                    content = bytes,
                    status = statusCode,
                    headers = headersOf(HttpHeaders.ContentType, mimeType),
                )
            }
            else ->
                respond(
                    content = "Not Found",
                    status = HttpStatusCode.NotFound,
                )
        }
    }
