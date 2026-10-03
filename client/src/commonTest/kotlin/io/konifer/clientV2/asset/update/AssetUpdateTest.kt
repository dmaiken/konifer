package io.konifer.clientV2.asset.update

import io.konifer.client.asset.info.createInfoResponse
import io.konifer.client.harness.assertSignatureParameter
import io.konifer.client.harness.httpClient
import io.konifer.clientV2.KoniferClientV2
import io.konifer.clientV2.KoniferV2Result
import io.konifer.clientV2.internal.HmacSigningConfig
import io.konifer.clientV2.internal.KoniferUrlSigner
import io.konifer.common.http.ErrorResponse
import io.konifer.common.http.StoreAssetRequest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.io.IOException
import kotlinx.serialization.json.Json

class AssetUpdateTest :
    FunSpec({
        test("seeded update sends a single PUT and preserves unchanged metadata") {
            val asset =
                createInfoResponse().copy(
                    entryId = 42,
                    labels = mapOf("camera" to "phone"),
                    tags = setOf("draft", "featured"),
                )
            val expected =
                StoreAssetRequest(
                    alt = asset.alt,
                    labels = asset.labels + ("category" to "avatar"),
                    tags = setOf("featured"),
                )
            var requestCount = 0
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        requestCount++
                        request.method shouldBe HttpMethod.Put
                        request.url.encodedPath shouldBe "/assets/users/123/-/entry/42"
                        request.url.parameters.isEmpty() shouldBe true
                        val body = request.body.shouldBeInstanceOf<TextContent>()
                        body.contentType.toString() shouldBe "application/json"
                        Json.decodeFromString<StoreAssetRequest>(body.text) shouldBe expected
                        respond(Json.encodeToString(asset), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                }

            val update =
                KoniferClientV2(httpClient)
                    .assets("/users/123")
                    .updateAsset(asset)
                    .withLabel("category", "avatar")
                    .withoutTag("draft")

            requestCount shouldBe 0
            update.update() shouldBe KoniferV2Result.Success(asset)
            requestCount shouldBe 1
        }

        test("update permits explicit clearing of editable fields") {
            val update = StoreAssetRequest(alt = null, labels = emptyMap(), tags = emptySet())
            val asset = createInfoResponse().copy(entryId = 42)
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        val body = request.body.shouldBeInstanceOf<TextContent>()
                        Json.decodeFromString<StoreAssetRequest>(body.text) shouldBe update
                        respond(Json.encodeToString(asset), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                }

            KoniferClientV2(httpClient)
                .assets("users/123")
                .updateAsset(asset)
                .clearAlt()
                .replaceLabels(emptyMap())
                .replaceTags(emptySet())
                .update() shouldBe KoniferV2Result.Success(asset)
        }

        test("update branches snapshot metadata and remain independent across repeated requests") {
            val labels = mutableMapOf("camera" to "phone", "category" to "photo")
            val tags = mutableSetOf("draft", "shared")
            val asset = createInfoResponse().copy(entryId = 73, labels = labels, tags = tags)
            val requests = mutableListOf<StoreAssetRequest>()
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        request.method shouldBe HttpMethod.Put
                        request.url.encodedPath shouldBe "/assets/users/123/-/entry/73"
                        val body = request.body.shouldBeInstanceOf<TextContent>()
                        requests += Json.decodeFromString<StoreAssetRequest>(body.text)
                        respond(Json.encodeToString(asset), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                }
            val expectedBase = StoreAssetRequest(alt = asset.alt, labels = labels.toMap(), tags = tags.toSet())
            val base = KoniferClientV2(httpClient).assets("users/123").updateAsset(asset)
            val avatar =
                base
                    .withAlt("Avatar")
                    .withLabel("category", "avatar")
                    .withoutLabel("camera")
                    .withTag("portrait")
                    .withoutTag("draft")
            val additionalLabels = mutableMapOf("category" to "banner", "campaign" to "fall")
            val additionalTags = mutableSetOf("shared", "featured")
            val banner = base.withLabels(additionalLabels).withTags(additionalTags)
            val replacement = base.replaceLabels(additionalLabels).replaceTags(additionalTags)

            val expectedAvatar =
                expectedBase.copy(alt = "Avatar", labels = mapOf("category" to "avatar"), tags = setOf("shared", "portrait"))
            val expectedBanner =
                expectedBase.copy(
                    labels = mapOf("camera" to "phone", "category" to "banner", "campaign" to "fall"),
                    tags = setOf("draft", "shared", "featured"),
                )
            val expectedReplacement = expectedBase.copy(labels = additionalLabels.toMap(), tags = additionalTags.toSet())

            labels.clear()
            tags.clear()
            additionalLabels.clear()
            additionalTags.clear()
            requests.size shouldBe 0

            listOf(base, avatar, banner, replacement, avatar, base).forEach { update ->
                update.update() shouldBe KoniferV2Result.Success(asset)
            }
            requests shouldBe
                listOf(expectedBase, expectedAvatar, expectedBanner, expectedReplacement, expectedAvatar, expectedBase)
        }

        test("update preserves null alt and permits removing nonexistent metadata") {
            val asset = createInfoResponse().copy(entryId = 42, alt = null, labels = emptyMap(), tags = emptySet())
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        val body = request.body.shouldBeInstanceOf<TextContent>()
                        Json.decodeFromString<StoreAssetRequest>(body.text) shouldBe StoreAssetRequest()
                        respond(Json.encodeToString(asset), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                }

            KoniferClientV2(httpClient)
                .assets("users/123")
                .updateAsset(asset)
                .withoutLabel("missing")
                .withoutTag("missing")
                .update() shouldBe KoniferV2Result.Success(asset)
        }

        test("update does not sign the selected entry URL") {
            val asset = createInfoResponse().copy(entryId = 42)
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        request.url.encodedPath shouldBe "/assets/users/123/-/entry/42"
                        assertSignatureParameter(request.url.parameters, false)
                        respond(Json.encodeToString(asset), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                }
            val client = KoniferClientV2(httpClient, KoniferUrlSigner.create(HmacSigningConfig(secretKey = "secret")))

            client
                .assets("users/123")
                .updateAsset(asset)
                .withAlt("updated")
                .update() shouldBe KoniferV2Result.Success(asset)
        }

        test("update maps an HTTP error to a V2 failure") {
            val httpClient =
                httpClient {
                    MockEngine {
                        respond(
                            Json.encodeToString(ErrorResponse("not found")),
                            status = HttpStatusCode.NotFound,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                }

            KoniferClientV2(httpClient).assets("users/123").updateAsset(createInfoResponse()).update() shouldBe
                KoniferV2Result.Failure.Http(404, "not found")
        }

        test("update maps malformed success responses to invalid response failures") {
            val httpClient =
                httpClient {
                    MockEngine {
                        respond("not-json", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                }

            val result = KoniferClientV2(httpClient).assets("users/123").updateAsset(createInfoResponse()).update()

            (result is KoniferV2Result.Failure.InvalidResponse) shouldBe true
        }

        test("update maps connection failures to transport failures") {
            val httpClient = httpClient { MockEngine { throw IOException("offline") } }

            val result = KoniferClientV2(httpClient).assets("users/123").updateAsset(createInfoResponse()).update()

            (result is KoniferV2Result.Failure.Transport) shouldBe true
        }
    })
