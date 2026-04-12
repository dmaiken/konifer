package io.konifer.client.content

import io.konifer.client.RequestedTransformation
import io.konifer.client.assertRequestedTransformation
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

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
