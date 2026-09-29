package io.konifer.client.asset.link

import io.konifer.client.RequestedTransformation
import io.konifer.client.harness.assertLabels
import io.konifer.client.harness.assertRequestedTransformation
import io.konifer.client.harness.assertSignatureParameter
import io.konifer.common.http.AssetLinkResponse
import io.konifer.common.http.AttributeResponse
import io.konifer.common.http.LQIPResponse
import io.konifer.common.image.ImageFormat
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.serialization.json.Json

fun createLinkResponse() =
    AssetLinkResponse(
        url = "https://localhost:9999",
        expiresAt = null,
        alt = "an image",
        lqip =
            LQIPResponse(
                blurhash = "blurhash",
                thumbhash = "thumbhash",
            ),
        attributes =
            AttributeResponse(
                height = 100,
                width = 200,
                format = ImageFormat.PNG.format,
                colorSpace = "srgb",
                loop = null,
                pageCount = 1,
            ),
    )

fun configureMockEngineHappy(
    expectedPath: String,
    response: AssetLinkResponse,
    statusCode: HttpStatusCode = HttpStatusCode.OK,
    requestedTransformation: RequestedTransformation? = null,
    labels: Map<String, String> = emptyMap(),
    expectSignature: Boolean = false,
): MockEngine =
    MockEngine { request ->
        request.url.encodedPath shouldBe expectedPath
        request.method shouldBe HttpMethod.Get
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
            content = Json.encodeToString(response),
            status = statusCode,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }
