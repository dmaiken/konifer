package io.konifer.client.link

import io.konifer.client.RequestedTransformation
import io.konifer.client.assertRequestedTransformation
import io.konifer.common.http.AssetLinkResponse
import io.konifer.common.http.LQIPResponse
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
        alt = "an image",
        lqip =
            LQIPResponse(
                blurhash = "blurhash",
                thumbhash = "thumbhash",
            ),
    )

fun configureMockEngineHappy(
    expectedPath: String,
    response: AssetLinkResponse,
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
            content = Json.encodeToString(response),
            status = statusCode,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }
