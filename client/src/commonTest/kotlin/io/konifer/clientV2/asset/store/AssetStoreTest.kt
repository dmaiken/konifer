package io.konifer.clientV2.asset.store

import io.konifer.client.asset.info.createInfoResponse
import io.konifer.client.harness.assertSignatureParameter
import io.konifer.client.harness.httpClient
import io.konifer.clientV2.KoniferClientV2
import io.konifer.clientV2.KoniferV2Result
import io.konifer.clientV2.internal.HmacSigningConfig
import io.konifer.clientV2.internal.KoniferUrlSigner
import io.konifer.common.http.AssetSourceRequest
import io.konifer.common.http.ErrorResponse
import io.konifer.common.http.HttpSource
import io.konifer.common.http.S3Source
import io.konifer.common.http.StoreAssetRequest
import io.konifer.common.image.ImageFormat
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
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
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.toByteArray
import kotlinx.coroutines.flow.flow
import kotlinx.io.IOException
import kotlinx.serialization.json.Json

@OptIn(InternalAPI::class)
class AssetStoreTest :
    FunSpec({
        test("store from URL posts JSON with the chosen source and asset details") {
            val asset = createInfoResponse()
            val expected =
                StoreAssetRequest(
                    alt = "An image",
                    labels = mapOf("camera" to "phone"),
                    tags = setOf("featured"),
                    source = AssetSourceRequest(http = HttpSource(url = "https://example.com/image.png")),
                )
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        request.method shouldBe HttpMethod.Post
                        request.url.encodedPath shouldBe "/assets/users/123"
                        val body = request.body.shouldBeInstanceOf<TextContent>()
                        body.contentType.toString() shouldBe "application/json"
                        Json.decodeFromString<StoreAssetRequest>(body.text) shouldBe expected
                        respond(
                            Json.encodeToString(asset),
                            status = HttpStatusCode.Created,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                }

            KoniferClientV2(httpClient)
                .assets("/users/123")
                .newAsset()
                .fromUrl("https://example.com/image.png")
                .withAlt("An image")
                .withLabel("camera", "phone")
                .withTags(setOf("featured"))
                .store() shouldBe KoniferV2Result.Success(asset)
        }

        test("metadata branches preserve their base and snapshot caller collections") {
            val asset = createInfoResponse()
            val requests = mutableListOf<StoreAssetRequest>()
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        val body = request.body.shouldBeInstanceOf<TextContent>()
                        requests += Json.decodeFromString<StoreAssetRequest>(body.text)
                        respond(Json.encodeToString(asset), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                }
            val url = "https://example.com/image.png"
            val base =
                KoniferClientV2(httpClient)
                    .assets("users/123")
                    .newAsset()
                    .fromUrl(url)
                    .withAlt("Base")
                    .withLabel("role", "base")
                    .withTag("shared")
            val avatar = base.withAlt("Avatar").withLabel("role", "avatar").withTag("portrait")
            val labels = mutableMapOf("role" to "banner", "campaign" to "fall")
            val tags = mutableSetOf("shared", "featured")
            val banner = base.withAlt("Banner").withLabels(labels).withTags(tags)

            labels.clear()
            tags.clear()

            base.store() shouldBe KoniferV2Result.Success(asset)
            avatar.store() shouldBe KoniferV2Result.Success(asset)
            banner.store() shouldBe KoniferV2Result.Success(asset)
            avatar.store() shouldBe KoniferV2Result.Success(asset)
            base.store() shouldBe KoniferV2Result.Success(asset)

            val expectedBase =
                StoreAssetRequest(
                    alt = "Base",
                    labels = mapOf("role" to "base"),
                    tags = setOf("shared"),
                    source = AssetSourceRequest(http = HttpSource(url = url)),
                )
            val expectedAvatar =
                expectedBase.copy(
                    alt = "Avatar",
                    labels = mapOf("role" to "avatar"),
                    tags = setOf("shared", "portrait"),
                )
            val expectedBanner =
                expectedBase.copy(
                    alt = "Banner",
                    labels = mapOf("role" to "banner", "campaign" to "fall"),
                    tags = setOf("shared", "featured"),
                )
            requests shouldBe listOf(expectedBase, expectedAvatar, expectedBanner, expectedAvatar, expectedBase)
        }

        test("store from S3 ARN posts the S3 source") {
            val asset = createInfoResponse()
            val arn = "arn:aws:s3:::images/example.png"
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        request.url.encodedPath shouldBe "/assets/users/123"
                        val body = request.body.shouldBeInstanceOf<TextContent>()
                        Json.decodeFromString<StoreAssetRequest>(body.text) shouldBe
                            StoreAssetRequest(source = AssetSourceRequest(s3 = S3Source(arn = arn)))
                        respond(
                            Json.encodeToString(asset),
                            status = HttpStatusCode.Created,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                }

            KoniferClientV2(httpClient)
                .assets("users/123")
                .newAsset()
                .fromS3Arn(arn)
                .store() shouldBe
                KoniferV2Result.Success(asset)
        }

        test("store from bytes sends metadata and the supplied image content type") {
            val asset = createInfoResponse()
            val bytes = byteArrayOf(1, 2, 3, 4)
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        request.method shouldBe HttpMethod.Post
                        request.url.encodedPath shouldBe "/assets/users/123"
                        val parts = request.body.shouldBeInstanceOf<MultiPartFormDataContent>().parts
                        val metadata = parts.filterIsInstance<PartData.FormItem>().single { it.name == "metadata" }
                        Json.decodeFromString<StoreAssetRequest>(metadata.value) shouldBe
                            StoreAssetRequest(labels = mapOf("camera" to "phone"), tags = setOf("featured"))
                        val content = parts.filterIsInstance<PartData.BinaryChannelItem>().single { it.name == "asset" }
                        content.headers[HttpHeaders.ContentType] shouldBe ImageFormat.PNG.mimeType
                        content.provider().toByteArray() shouldBe bytes
                        respond(
                            Json.encodeToString(asset),
                            status = HttpStatusCode.Created,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                }

            KoniferClientV2(httpClient)
                .assets("users/123")
                .newAsset()
                .fromBytes(bytes, ImageFormat.PNG)
                .withLabels(mapOf("camera" to "phone"))
                .withTag("featured")
                .store() shouldBe KoniferV2Result.Success(asset)
        }

        test("byte uploads preserve their snapshot across caller mutations and repeated stores") {
            val asset = createInfoResponse()
            val bytes = byteArrayOf(1, 2, 3, 4)
            val expected = bytes.copyOf()
            var requests = 0
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        requests++
                        val parts = request.body.shouldBeInstanceOf<MultiPartFormDataContent>().parts
                        val content = parts.filterIsInstance<PartData.BinaryChannelItem>().single { it.name == "asset" }
                        content.provider().toByteArray() shouldBe expected
                        content.provider().toByteArray() shouldBe expected
                        respond(Json.encodeToString(asset), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                }
            val upload =
                KoniferClientV2(httpClient)
                    .assets("users/123")
                    .newAsset()
                    .fromBytes(bytes, ImageFormat.PNG)

            bytes.fill(0)
            upload.store() shouldBe KoniferV2Result.Success(asset)
            bytes.fill(9)
            upload.store() shouldBe KoniferV2Result.Success(asset)
            requests shouldBe 2
        }

        test("store from channel opens a fresh channel for each read and request") {
            val asset = createInfoResponse()
            val bytes = byteArrayOf(9, 8, 7)
            var opens = 0
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        val parts = request.body.shouldBeInstanceOf<MultiPartFormDataContent>().parts
                        val content = parts.filterIsInstance<PartData.BinaryChannelItem>().single { it.name == "asset" }
                        content.headers[HttpHeaders.ContentType] shouldBe ImageFormat.JPEG.mimeType
                        content.provider().toByteArray() shouldBe bytes
                        content.provider().toByteArray() shouldBe bytes
                        respond(
                            Json.encodeToString(asset),
                            status = HttpStatusCode.Created,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                }

            val upload =
                KoniferClientV2(httpClient)
                    .assets("users/123")
                    .newAsset()
                    .fromChannel({
                        opens++
                        ByteReadChannel(bytes)
                    }, ImageFormat.JPEG)
            upload.store() shouldBe KoniferV2Result.Success(asset)
            upload.store() shouldBe KoniferV2Result.Success(asset)
            opens shouldBe 4
        }

        test("store from chunks collects a fresh flow for each read") {
            val asset = createInfoResponse()
            val bytes = byteArrayOf(1, 2, 3, 4)
            var collections = 0
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        val parts = request.body.shouldBeInstanceOf<MultiPartFormDataContent>().parts
                        val content = parts.filterIsInstance<PartData.BinaryChannelItem>().single { it.name == "asset" }
                        content.headers[HttpHeaders.ContentType] shouldBe ImageFormat.PNG.mimeType
                        content.provider().toByteArray() shouldBe bytes
                        content.provider().toByteArray() shouldBe bytes
                        respond(Json.encodeToString(asset), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                }

            val upload =
                KoniferClientV2(httpClient)
                    .assets("users/123")
                    .newAsset()
                    .fromChunks(
                        chunks = {
                            flow {
                                collections++
                                emit(byteArrayOf(1, 2))
                                emit(byteArrayOf(3, 4))
                            }
                        },
                        format = ImageFormat.PNG,
                    )
            upload.store() shouldBe KoniferV2Result.Success(asset)
            upload.store() shouldBe KoniferV2Result.Success(asset)
            collections shouldBe 4
        }

        test("store does not sign the asset path even with a signed client") {
            val asset = createInfoResponse()
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        request.url.encodedPath shouldBe "/assets/users/123"
                        assertSignatureParameter(request.url.parameters, false)
                        respond(
                            Json.encodeToString(asset),
                            status = HttpStatusCode.Created,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                }
            val client = KoniferClientV2(httpClient, KoniferUrlSigner.create(HmacSigningConfig(secretKey = "secret")))

            client
                .assets("users/123")
                .newAsset()
                .fromUrl("https://example.com/image.png")
                .store() shouldBe
                KoniferV2Result.Success(asset)
        }

        test("multipart upload does not sign the asset path even with a signed client") {
            val asset = createInfoResponse()
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        request.method shouldBe HttpMethod.Post
                        request.url.encodedPath shouldBe "/assets/users/123"
                        assertSignatureParameter(request.url.parameters, false)
                        request.body.shouldBeInstanceOf<MultiPartFormDataContent>()
                        respond(
                            Json.encodeToString(asset),
                            status = HttpStatusCode.Created,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                }
            val client = KoniferClientV2(httpClient, KoniferUrlSigner.create(HmacSigningConfig(secretKey = "secret")))

            client
                .assets("users/123")
                .newAsset()
                .fromBytes(byteArrayOf(1, 2, 3), ImageFormat.PNG)
                .store() shouldBe
                KoniferV2Result.Success(asset)
        }

        test("blank external source fails before making a request") {
            val httpClient = httpClient { MockEngine { error("No request expected") } }

            shouldThrow<IllegalArgumentException> {
                KoniferClientV2(httpClient)
                    .assets("users/123")
                    .newAsset()
                    .fromUrl(" ")
                    .store()
            }
        }

        test("store maps an HTTP error to a V2 failure") {
            val httpClient =
                httpClient {
                    MockEngine {
                        respond(
                            Json.encodeToString(ErrorResponse("invalid asset")),
                            status = HttpStatusCode.BadRequest,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                }

            KoniferClientV2(httpClient)
                .assets("users/123")
                .newAsset()
                .fromS3Arn("arn:aws:s3:::images/image.png")
                .store() shouldBe
                KoniferV2Result.Failure.Http(400, "invalid asset")
        }

        test("store maps malformed success responses to invalid response failures") {
            val httpClient =
                httpClient {
                    MockEngine {
                        respond(
                            "not-json",
                            status = HttpStatusCode.Created,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                }

            val result =
                KoniferClientV2(httpClient)
                    .assets("users/123")
                    .newAsset()
                    .fromUrl("https://example.com/image.png")
                    .store()
            (result is KoniferV2Result.Failure.InvalidResponse) shouldBe true
        }

        test("store maps connection failures to transport failures") {
            val httpClient = httpClient { MockEngine { throw IOException("offline") } }

            val result =
                KoniferClientV2(httpClient)
                    .assets("users/123")
                    .newAsset()
                    .fromUrl("https://example.com/image.png")
                    .store()
            (result is KoniferV2Result.Failure.Transport) shouldBe true
        }
    })
