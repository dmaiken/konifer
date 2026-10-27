package io.konifer.asset

import io.konifer.BaseFunctionalTest
import io.konifer.common.http.ErrorCode
import io.konifer.common.http.ErrorResponse
import io.konifer.testInMemory
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.junit.jupiter.api.Test

class ErrorResponseApiTest : BaseFunctionalTest() {
    @Test
    fun `invalid request inputs use stable codes`() =
        testInMemory {
            val paths =
                listOf(
                    "/assets/avatar/-/entry/nope/info" to ErrorCode.INVALID_PATH,
                    "/assets/avatar/-/nope" to ErrorCode.INVALID_PATH,
                    "/assets/avatar/-/info?limit=nope" to ErrorCode.INVALID_REQUEST,
                    "/assets/avatar/-/info?limit=1&limit=2" to ErrorCode.INVALID_REQUEST,
                    "/assets/avatar/-/link?width=nope" to ErrorCode.INVALID_TRANSFORMATION,
                    "/assets/avatar/-/link?width=0" to ErrorCode.INVALID_TRANSFORMATION,
                    "/assets/avatar/-/link?quality=101" to ErrorCode.INVALID_TRANSFORMATION,
                    "/assets/avatar/-/link?fit=nope" to ErrorCode.INVALID_TRANSFORMATION,
                    "/assets/avatar/-/link?rotate=45" to ErrorCode.INVALID_TRANSFORMATION,
                    "/assets/avatar/-/link?pixel-layout=tiled" to ErrorCode.INVALID_TRANSFORMATION,
                    "/assets/avatar/-/link?pixel-channels=xx" to ErrorCode.INVALID_TRANSFORMATION,
                    "/assets/avatar/-/link?fit=crop&width=10" to ErrorCode.INVALID_TRANSFORMATION,
                    "/assets/avatar/-/link?profile=missing" to ErrorCode.INVALID_TRANSFORMATION,
                )
            paths.forEach { (path, code) ->
                val response = client.get(path)
                response.status.value shouldBe 400
                response.body<ErrorResponse>().code shouldBe code
            }
        }

    @Test
    fun `missing assets and unsupported media types return JSON`() =
        testInMemory {
            listOf("info", "link", "redirect", "content", "download").forEach { format ->
                val response = client.get("/assets/missing/-/$format")
                response.status.value shouldBe 404
                response.body<ErrorResponse>().code shouldBe ErrorCode.NOT_FOUND
            }
            val disabledRoute = client.get("/rule-evaluations/missing")
            disabledRoute.status.value shouldBe 404
            disabledRoute.body<ErrorResponse>().code shouldBe ErrorCode.NOT_FOUND
            val unsupported =
                client.post("/assets/avatar") {
                    contentType(ContentType.Text.Plain)
                    setBody("image")
                }
            unsupported.status.value shouldBe 415
            unsupported.body<ErrorResponse>().code shouldBe ErrorCode.UNSUPPORTED_MEDIA_TYPE
        }

    @Test
    fun `JSON and multipart decoding failures remain bad requests`() =
        testInMemory {
            val invalidJson =
                client.post("/assets/avatar") {
                    contentType(ContentType.Application.Json)
                    setBody("{")
                }
            invalidJson.status.value shouldBe 400
            invalidJson.body<ErrorResponse>().code shouldBe ErrorCode.INVALID_REQUEST
            val invalidMetadata =
                client.post("/assets/avatar") {
                    setBody(MultiPartFormDataContent(formData { append("metadata", "{") }))
                }
            invalidMetadata.status.value shouldBe 400
            invalidMetadata.body<ErrorResponse>().code shouldBe ErrorCode.INVALID_REQUEST
            val missingParts =
                client.post("/assets/avatar") {
                    setBody(MultiPartFormDataContent(formData { append("metadata", "{}") }))
                }
            missingParts.status.value shouldBe 400
            missingParts.body<ErrorResponse>().code shouldBe ErrorCode.INVALID_REQUEST
            val invalidLabels = client.get("/assets/avatar/-/info?label:=value")
            invalidLabels.status.value shouldBe 400
            invalidLabels.body<ErrorResponse>().code shouldBe ErrorCode.INVALID_REQUEST
        }
}
