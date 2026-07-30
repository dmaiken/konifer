package io.konifer.client.asset.update

import io.konifer.common.http.AssetResponse
import io.konifer.common.http.StoreAssetRequest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngine.Companion.invoke
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.serialization.json.Json

fun configureMockEngineHappy(
    expectedPath: String,
    request: StoreAssetRequest,
    response: AssetResponse,
    statusCode: HttpStatusCode = HttpStatusCode.OK,
): MockEngine =
    MockEngine { httpRequest ->
        httpRequest.url.encodedPath shouldBe expectedPath
        httpRequest.method shouldBe HttpMethod.Put

        val body = httpRequest.body.shouldBeInstanceOf<TextContent>()
        Json.decodeFromString<StoreAssetRequest>(body.text) shouldBe request

        respond(
            content = Json.encodeToString(response),
            status = statusCode,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }
