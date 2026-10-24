package io.konifer.asset

import io.konifer.BaseFunctionalTest
import io.konifer.ImageFactory
import io.konifer.common.http.AssetLinkResponse
import io.konifer.common.http.AssetResponse
import io.konifer.common.http.StoreAssetRequest
import io.konifer.domain.ports.ObjectStore
import io.konifer.domain.ports.PresignedUrl
import io.konifer.infrastructure.objectstore.inmemory.InMemoryObjectStore
import io.konifer.testInMemory
import io.konifer.util.storeAssetMultipartSource
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.ktor.client.call.body
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.toKotlinInstant
import kotlin.time.Instant as KotlinInstant

class TimestampApiTest : BaseFunctionalTest() {
    @Test
    fun `store update info and entries expose UTC instant timestamps while HTTP cache headers retain their format`() =
        testInMemory {
            val stored =
                client.submitFormWithBinaryData(
                    url = "/assets/timestamps",
                    formData =
                        formData {
                            append(
                                "metadata",
                                """{"alt":"original"}""",
                                Headers.build {
                                    append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                },
                            )
                            append(
                                "asset",
                                ImageFactory.testImage().bytes,
                                Headers.build {
                                    append(HttpHeaders.ContentType, "image/jpeg")
                                    append(HttpHeaders.ContentDisposition, "filename=image.jpeg")
                                },
                            )
                        },
                )
            stored.status shouldBe HttpStatusCode.Created
            val storedJson = stored.json()
            storedJson.assertTimestamps()
            val storedModel = stored.body<AssetResponse>()
            val updated =
                client.put(stored.headers[HttpHeaders.Location]!!) {
                    contentType(ContentType.Application.Json)
                    setBody(StoreAssetRequest(alt = "updated"))
                }
            updated.status shouldBe HttpStatusCode.OK
            val updatedJson = updated.json()
            updatedJson.assertTimestamps()
            val updatedModel = updated.body<AssetResponse>()
            updatedModel.createdAt shouldBe storedModel.createdAt
            (updatedModel.modifiedAt >= storedModel.modifiedAt) shouldBe true
            val info = client.get("/assets/timestamps/-/info")
            info.status shouldBe HttpStatusCode.OK
            info.json().assertTimestamps()
            val entries = client.get("/assets/timestamps/-/entries")
            entries.status shouldBe HttpStatusCode.OK
            entries
                .json()
                .getValue("entries")
                .jsonArray
                .single()
                .jsonObject
                .assertTimestamps()
            val content = client.get("/assets/timestamps/-/content")
            content.status shouldBe HttpStatusCode.OK
            val lastModified = checkNotNull(content.headers[HttpHeaders.LastModified])
            lastModified shouldEndWith "GMT"
            client
                .get("/assets/timestamps/-/content") {
                    header(HttpHeaders.IfModifiedSince, lastModified)
                }.status shouldBe HttpStatusCode.NotModified
            client
                .get("/assets/timestamps/-/content") {
                    header(HttpHeaders.IfNoneMatch, checkNotNull(content.headers[HttpHeaders.ETag]))
                }.status shouldBe HttpStatusCode.NotModified
        }

    @Test
    fun `presigned link expiry is a UTC instant without losing precision`() {
        val expiry = Instant.parse("2030-01-02T03:04:05.123456789Z")
        val objectStores =
            module {
                single<ObjectStore> {
                    object : ObjectStore by InMemoryObjectStore() {
                        override suspend fun generatePresignedUrl(
                            bucket: String,
                            key: String,
                            ttl: Duration,
                        ): PresignedUrl = PresignedUrl.Supported(Url("https://objects.example.com/image"), expiry)
                    }
                }
            }
        testInMemory(
            configuration = """paths { "/**" { delivery.strategy = presigned } }""",
            modules = listOf(objectStores),
        ) {
            storeAssetMultipartSource(client, ImageFactory.testImage().bytes, StoreAssetRequest(), path = "timestamps")
            val response = client.get("/assets/timestamps/-/link")
            response.status shouldBe HttpStatusCode.OK
            response
                .json()
                .getValue("expiresAt")
                .jsonPrimitive.content shouldBe expiry.toString()
            response.body<AssetLinkResponse>().expiresAt shouldBe expiry.toKotlinInstant()
        }
    }

    private suspend fun HttpResponse.json(): JsonObject = Json.parseToJsonElement(bodyAsText()).jsonObject

    private fun JsonObject.assertTimestamps() {
        for (field in listOf("createdAt", "modifiedAt")) {
            val value = getValue(field).jsonPrimitive.content
            value shouldEndWith "Z"
            KotlinInstant.parse(value).toString() shouldBe value
        }
    }
}
