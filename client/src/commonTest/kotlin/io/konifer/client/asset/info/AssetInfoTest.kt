package io.konifer.client.asset.info

import io.konifer.client.KoniferClient
import io.konifer.client.KoniferResult
import io.konifer.client.harness.assertLabels
import io.konifer.client.harness.assertSignatureParameter
import io.konifer.client.harness.httpClient
import io.konifer.client.internal.HmacSigningConfig
import io.konifer.client.internal.KoniferUrlSigner
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

            KoniferClient(httpClient).assets("/users/123").fetchInfo() shouldBe KoniferResult.Success(info)
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

            KoniferClient(httpClient)
                .assets("users/123")
                .matchingLabels(labels)
                .orderBy(Order.MODIFIED)
                .fetchInfo() shouldBe KoniferResult.Success(info)
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
            val client = KoniferClient(httpClient, KoniferUrlSigner.create(HmacSigningConfig(secretKey = "secret")))

            client.assets("users/123").entry(42).fetchInfo() shouldBe KoniferResult.Success(info)
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

            KoniferClient(httpClient).assets("users/123").entry(42).fetchInfo() shouldBe
                KoniferResult.Failure.Http(404, "not found")
        }

        test("info retains HTTP status when the error body is not JSON") {
            val httpClient =
                httpClient {
                    MockEngine {
                        respond("not-json", status = HttpStatusCode.NotFound, headers = headersOf(HttpHeaders.ContentType, "text/plain"))
                    }
                }

            KoniferClient(httpClient).assets("users/123").fetchInfo() shouldBe
                KoniferResult.Failure.Http(404, null)
        }

        test("info maps malformed success bodies to invalid response failures") {
            val httpClient =
                httpClient {
                    MockEngine {
                        respond("not-json", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                }

            val result = KoniferClient(httpClient).assets("users/123").fetchInfo()
            (result is KoniferResult.Failure.InvalidResponse) shouldBe true
        }
    })
