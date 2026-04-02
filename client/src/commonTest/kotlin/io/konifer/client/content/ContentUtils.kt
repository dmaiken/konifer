package io.konifer.client.content

import io.konifer.client.QuerySelectors
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

fun createContentResponse(
    expectedPath: String,
    bytes: ByteArray,
    mimeType: String = "image/png",
    statusCode: HttpStatusCode = HttpStatusCode.OK,
): MockEngine =
    MockEngine { request ->
        request.url.encodedPath shouldBe expectedPath

        respond(
            content = bytes,
            status = statusCode,
            headers = headersOf(HttpHeaders.ContentType, mimeType),
        )
    }
