package io.konifer.client.asset.redirect

import io.konifer.client.RequestedTransformation
import io.konifer.client.harness.assertLabels
import io.konifer.client.harness.assertRequestedTransformation
import io.konifer.client.harness.assertSignatureParameter
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
    labels: Map<String, String> = emptyMap(),
    expectSignature: Boolean = false,
): MockEngine =
    MockEngine { request ->
        request.method shouldBe HttpMethod.Get
        request.url.encodedPath shouldBe expectedPath

        assertRequestedTransformation(
            parameters = request.url.parameters,
            requestedTransformation = requestedTransformation,
        )
        assertLabels(
            parameters = request.url.parameters,
            labels = labels,
        )
        assertSignatureParameter(
            parameters = request.url.parameters,
            expectSignature = expectSignature,
        )
        respond(
            content = "",
            status = statusCode,
            headers = headersOf(HttpHeaders.Location, redirectLocation),
        )
    }
