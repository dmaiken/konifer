package io.konifer.clientV2.asset.link

import io.konifer.client.asset.link.createLinkResponse
import io.konifer.client.harness.assertLabels
import io.konifer.client.harness.assertRequestedTransformation
import io.konifer.client.harness.assertSignatureParameter
import io.konifer.client.harness.httpClient
import io.konifer.clientV2.KoniferClientV2
import io.konifer.clientV2.KoniferV2Result
import io.konifer.clientV2.assets.fetch.requestedTransformation
import io.konifer.clientV2.internal.HmacSigningConfig
import io.konifer.clientV2.internal.KoniferUrlSigner
import io.konifer.common.http.ErrorResponse
import io.konifer.common.selector.Order
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.serialization.json.Json

class AssetLinkTest :
    FunSpec({
        test("link decodes the newest variant response and signs the request") {
            val link = createLinkResponse()
            val transformation = requestedTransformation { height = 120 }
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        request.url.encodedPath shouldBe "/assets/users/123/-/new/link"
                        assertRequestedTransformation(request.url.parameters, transformation)
                        assertSignatureParameter(request.url.parameters, true)
                        request.headers[HttpHeaders.Accept] shouldBe "application/json"
                        respond(
                            Json.encodeToString(link),
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                }
            val client = KoniferClientV2(httpClient, KoniferUrlSigner.create(HmacSigningConfig(secretKey = "secret")))

            client.asset("users/123").variant(transformation).link() shouldBe KoniferV2Result.Success(link)
        }

        test("link includes labels and modified ordering") {
            val link = createLinkResponse()
            val labels = mapOf("Camera" to "phone", "format" to "display")
            val transformation = requestedTransformation { width = 80 }
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        request.url.encodedPath shouldBe "/assets/users/123/-/modified/link"
                        assertLabels(request.url.parameters, labels)
                        assertRequestedTransformation(request.url.parameters, transformation)
                        respond(Json.encodeToString(link), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                }

            KoniferClientV2(httpClient)
                .asset("users/123")
                .matchingLabels(labels)
                .orderBy(Order.MODIFIED)
                .variant(transformation)
                .link() shouldBe KoniferV2Result.Success(link)
        }

        test("link can select an entry by ID") {
            val link = createLinkResponse()
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        request.url.encodedPath shouldBe "/assets/users/123/-/entry/42/link"
                        respond(Json.encodeToString(link), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                }

            KoniferClientV2(httpClient)
                .asset("users/123")
                .entry(42)
                .variant(requestedTransformation {})
                .link() shouldBe
                KoniferV2Result.Success(link)
        }

        test("link maps HTTP errors to V2 failures") {
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

            KoniferClientV2(httpClient).asset("users/123").variant(requestedTransformation {}).link() shouldBe
                KoniferV2Result.Failure.Http(404, "not found")
        }

        test("malformed link response is an invalid response failure") {
            val httpClient =
                httpClient {
                    MockEngine {
                        respond("not-json", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                }

            val result = KoniferClientV2(httpClient).asset("users/123").variant(requestedTransformation {}).link()

            (result is KoniferV2Result.Failure.InvalidResponse) shouldBe true
        }
    })
