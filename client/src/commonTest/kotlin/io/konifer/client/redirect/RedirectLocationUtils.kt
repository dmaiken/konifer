package io.konifer.client.redirect

import io.konifer.client.RequestedTransformation
import io.konifer.client.harness.assertRequestedTransformation
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

fun configureMockEngineHappyRedirect(
    expectedPath: String,
    redirectLocation: String,
    statusCode: HttpStatusCode = HttpStatusCode.TemporaryRedirect,
    requestedTransformation: RequestedTransformation? = null,
): MockEngine =
    MockEngine { request ->
        request.method shouldBe HttpMethod.Get
        request.url.encodedPath shouldBe expectedPath

        assertRequestedTransformation(
            parameters = request.url.parameters,
            requestedTransformation = requestedTransformation,
        )
        respond(
            content = "",
            status = statusCode,
            headers = headersOf(HttpHeaders.Location, redirectLocation),
        )
    }
