package io.konifer.clientV2.asset.update

import io.konifer.client.asset.info.createInfoResponse
import io.konifer.client.harness.assertSignatureParameter
import io.konifer.client.harness.httpClient
import io.konifer.clientV2.KoniferClientV2
import io.konifer.clientV2.internal.HmacSigningConfig
import io.konifer.clientV2.internal.KoniferUrlSigner
import io.konifer.clientV2.KoniferV2Result
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
        test("update sends a single PUT for the selected entry and decodes the asset") {
            val update = StoreAssetRequest(alt = "new alt", labels = mapOf("camera" to "phone"), tags = setOf("featured"))
            val asset = createInfoResponse()
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
                        Json.decodeFromString<StoreAssetRequest>(body.text) shouldBe update
                        respond(Json.encodeToString(asset), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                }

            KoniferClientV2(httpClient).asset("/users/123").entry(42).update(update) shouldBe KoniferV2Result.Success(asset)
            requestCount shouldBe 1
        }

        test("update permits explicit clearing of editable fields") {
            val update = StoreAssetRequest(alt = null, labels = emptyMap(), tags = emptySet())
            val asset = createInfoResponse()
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        val body = request.body.shouldBeInstanceOf<TextContent>()
                        Json.decodeFromString<StoreAssetRequest>(body.text) shouldBe update
                        respond(Json.encodeToString(asset), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                }

            KoniferClientV2(httpClient).asset("users/123").entry(42).update(update) shouldBe KoniferV2Result.Success(asset)
        }

        test("update does not sign the selected entry URL") {
            val update = StoreAssetRequest(alt = "updated")
            val asset = createInfoResponse()
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        request.url.encodedPath shouldBe "/assets/users/123/-/entry/42"
                        assertSignatureParameter(request.url.parameters, false)
                        respond(Json.encodeToString(asset), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                }
            val client = KoniferClientV2(httpClient, KoniferUrlSigner.create(HmacSigningConfig(secretKey = "secret")))

            client.asset("users/123").entry(42).update(update) shouldBe KoniferV2Result.Success(asset)
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

            KoniferClientV2(httpClient).asset("users/123").entry(42).update(StoreAssetRequest()) shouldBe
                KoniferV2Result.Failure.Http(404, "not found")
        }

        test("update maps malformed success responses to invalid response failures") {
            val httpClient =
                httpClient {
                    MockEngine {
                        respond("not-json", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                }

            val result = KoniferClientV2(httpClient).asset("users/123").entry(42).update(StoreAssetRequest())

            (result is KoniferV2Result.Failure.InvalidResponse) shouldBe true
        }

        test("update maps connection failures to transport failures") {
            val httpClient = httpClient { MockEngine { throw IOException("offline") } }

            val result = KoniferClientV2(httpClient).asset("users/123").entry(42).update(StoreAssetRequest())

            (result is KoniferV2Result.Failure.Transport) shouldBe true
        }
    })
