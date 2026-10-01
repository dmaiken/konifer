package io.konifer.clientV2.asset.info

import io.konifer.client.asset.info.createInfoResponse
import io.konifer.client.harness.assertLabels
import io.konifer.client.harness.assertSignatureParameter
import io.konifer.client.harness.httpClient
import io.konifer.clientV2.KoniferClientV2
import io.konifer.clientV2.internal.HmacSigningConfig
import io.konifer.clientV2.internal.KoniferUrlSigner
import io.konifer.clientV2.KoniferV2Result
import io.konifer.common.http.ErrorResponse
import io.konifer.common.selector.Order
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.serialization.json.Json

class AssetInfoTest :
    FunSpec({
        test("default path selection fetches newest asset info") {
            val info = createInfoResponse()
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        request.method shouldBe HttpMethod.Get
                        request.url.encodedPath shouldBe "/assets/users/123/-/new/info"
                        request.url.parameters.isEmpty() shouldBe true
                        request.headers[HttpHeaders.Accept] shouldBe "application/json"
                        respond(Json.encodeToString(info), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                }

            KoniferClientV2(httpClient).asset("/users/123").info() shouldBe KoniferV2Result.Success(info)
        }

        test("relative selection includes order and labels") {
            val info = createInfoResponse()
            val labels = mapOf("Camera" to "phone", "format" to "display")
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        request.url.encodedPath shouldBe "/assets/users/123/-/modified/info"
                        assertLabels(request.url.parameters, labels)
                        respond(Json.encodeToString(info), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                }

            KoniferClientV2(httpClient)
                .asset("users/123")
                .matchingLabels(labels)
                .orderBy(Order.MODIFIED)
                .info() shouldBe KoniferV2Result.Success(info)
        }

        test("absolute selection fetches the specified entry and signs the request") {
            val info = createInfoResponse()
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        request.url.encodedPath shouldBe "/assets/users/123/-/entry/42/info"
                        assertSignatureParameter(request.url.parameters, true)
                        respond(Json.encodeToString(info), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                }
            val client = KoniferClientV2(httpClient, KoniferUrlSigner.create(HmacSigningConfig(secretKey = "secret")))

            client.asset("users/123").entry(42).info() shouldBe KoniferV2Result.Success(info)
        }

        test("limited info fetches a list and includes limit and labels") {
            val info = listOf(createInfoResponse(), createInfoResponse())
            val labels = mapOf("limit" to "important")
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        request.url.encodedPath shouldBe "/assets/users/123/-/new/info"
                        request.url.parameters["limit"] shouldBe "2"
                        assertLabels(request.url.parameters, labels)
                        respond(Json.encodeToString(info), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                }

            KoniferClientV2(httpClient)
                .asset("users/123")
                .matchingLabels(labels)
                .info(2) shouldBe KoniferV2Result.Success(info)
        }

        test("a limit of one returns a list from the server's single asset response") {
            val info = createInfoResponse()
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        request.url.encodedPath shouldBe "/assets/users/123/-/new/info"
                        respond(Json.encodeToString(info), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                }

            KoniferClientV2(httpClient).asset("users/123").info(1) shouldBe KoniferV2Result.Success(listOf(info))
        }

        test("info maps HTTP errors to V2 failures") {
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

            KoniferClientV2(httpClient).asset("users/123").entry(42).info() shouldBe
                KoniferV2Result.Failure.Http(404, "not found")
        }

        test("info retains HTTP status when the error body is not JSON") {
            val httpClient =
                httpClient {
                    MockEngine {
                        respond("not-json", status = HttpStatusCode.NotFound, headers = headersOf(HttpHeaders.ContentType, "text/plain"))
                    }
                }

            KoniferClientV2(httpClient).asset("users/123").info() shouldBe
                KoniferV2Result.Failure.Http(404, null)
        }

        test("info maps malformed success bodies to invalid response failures") {
            val httpClient =
                httpClient {
                    MockEngine {
                        respond("not-json", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                }

            val result = KoniferClientV2(httpClient).asset("users/123").info()
            (result is KoniferV2Result.Failure.InvalidResponse) shouldBe true
        }
    })
