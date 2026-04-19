package io.konifer.client.store

import io.konifer.common.http.AssetResponse
import io.konifer.common.http.StoreAssetRequest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.toByteArray
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@OptIn(InternalAPI::class)
fun configureMockMultipartEngineHappy(
    expectedPath: String,
    request: StoreAssetRequest,
    assetBytes: ByteArray,
    response: AssetResponse,
    statusCode: HttpStatusCode = HttpStatusCode.OK,
): MockEngine =
    MockEngine { httpRequest ->
        httpRequest.url.encodedPath shouldBe expectedPath
        httpRequest.method shouldBe HttpMethod.Post

        val body = httpRequest.body.shouldBeInstanceOf<MultiPartFormDataContent>()
        val parts = body.parts

        val metadataPart =
            parts
                .filterIsInstance<PartData.FormItem>()
                .find { it.name == "metadata" }
        metadataPart shouldNotBe null
        metadataPart?.value shouldBe Json.encodeToString(request)

        val filePart = parts.find { it.name == "asset" }
        filePart shouldNotBe null

        when (filePart) {
            is PartData.BinaryChannelItem -> filePart.provider().toByteArray()
            else -> throw AssertionError("Asset part is not a FileItem or BinaryItem but is $filePart")
        } shouldBe assetBytes

        respond(
            content = Json.encodeToString(response),
            status = statusCode,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }

@OptIn(InternalAPI::class)
fun configureMockUrlEngineHappy(
    expectedPath: String,
    request: StoreAssetRequest,
    response: AssetResponse,
    statusCode: HttpStatusCode = HttpStatusCode.OK,
): MockEngine =
    MockEngine { httpRequest ->
        httpRequest.url.encodedPath shouldBe expectedPath
        httpRequest.method shouldBe HttpMethod.Post

        val body = httpRequest.body.shouldBeInstanceOf<TextContent>()
        Json.decodeFromString<StoreAssetRequest>(body.text) shouldBe request

        respond(
            content = Json.encodeToString(response),
            status = statusCode,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }
