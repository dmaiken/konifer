package io.konifer.client.asset.delete

import io.konifer.client.KoniferClient
import io.konifer.client.KoniferResult
import io.konifer.client.harness.assertLabels
import io.konifer.client.harness.assertSignatureParameter
import io.konifer.client.harness.httpClient
import io.konifer.client.internal.HmacSigningConfig
import io.konifer.client.internal.KoniferUrlSigner
import io.konifer.common.selector.Order
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.io.IOException

class AssetDeleteTest :
    FunSpec({
        test("delete all at a path explicitly sends the unlimited limit") {
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        request.method shouldBe HttpMethod.Delete
                        request.url.encodedPath shouldBe "/assets/users/123"
                        request.url.parameters["limit"] shouldBe "-1"
                        respond("", status = HttpStatusCode.NoContent)
                    }
                }

            KoniferClient(httpClient).assets("/users/123").deleteAll() shouldBe KoniferResult.Success(Unit)
        }

        test("delete all at a path supports labels and omits ordering") {
            val labels = mapOf("Camera" to "phone", "limit" to "important")
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        request.method shouldBe HttpMethod.Delete
                        request.url.encodedPath shouldBe "/assets/users/123"
                        request.url.parameters["limit"] shouldBe "-1"
                        assertLabels(request.url.parameters, labels)
                        respond("", status = HttpStatusCode.NoContent)
                    }
                }

            KoniferClient(httpClient)
                .assets("users/123")
                .matchingLabels(labels)
                .orderBy(Order.MODIFIED)
                .deleteAll() shouldBe KoniferResult.Success(Unit)
        }

        test("delete all is available directly at a path") {
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        request.url.encodedPath shouldBe "/assets/users/123"
                        request.url.parameters["limit"] shouldBe "-1"
                        respond("", status = HttpStatusCode.NoContent)
                    }
                }

            KoniferClient(httpClient).assets("users/123").deleteAll() shouldBe KoniferResult.Success(Unit)
        }

        test("delete by entry ID does not send relative options") {
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        request.method shouldBe HttpMethod.Delete
                        request.url.encodedPath shouldBe "/assets/users/123/-/entry/42"
                        request.url.parameters.isEmpty() shouldBe true
                        respond("", status = HttpStatusCode.NoContent)
                    }
                }

            KoniferClient(httpClient).assets("users/123").entry(42).delete() shouldBe KoniferResult.Success(Unit)
        }

        test("recursive delete is available directly at a path without a limit") {
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        request.method shouldBe HttpMethod.Delete
                        request.url.encodedPath shouldBe "/assets/users/123/-/recursive"
                        request.url.parameters.isEmpty() shouldBe true
                        respond("", status = HttpStatusCode.NoContent)
                    }
                }

            KoniferClient(httpClient).assets("users/123").deleteRecursively() shouldBe KoniferResult.Success(Unit)
        }

        test("recursive delete filters by labels without a limit or ordering") {
            val labels = mapOf("Camera" to "phone", "width" to "wide")
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        request.url.encodedPath shouldBe "/assets/users/123/-/recursive"
                        assertLabels(request.url.parameters, labels)
                        request.url.parameters["limit"] shouldBe null
                        respond("", status = HttpStatusCode.NoContent)
                    }
                }

            KoniferClient(httpClient)
                .assets("users/123")
                .matchingLabels(labels)
                .deleteRecursively() shouldBe KoniferResult.Success(Unit)
        }

        test("delete does not sign the final request URL") {
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        request.url.encodedPath shouldBe "/assets/users/123"
                        request.url.parameters["limit"] shouldBe "-1"
                        assertSignatureParameter(request.url.parameters, false)
                        respond("", status = HttpStatusCode.NoContent)
                    }
                }
            val client = KoniferClient(httpClient, KoniferUrlSigner.create(HmacSigningConfig(secretKey = "secret")))

            client.assets("users/123").deleteAll() shouldBe KoniferResult.Success(Unit)
        }

        test("delete maps an HTTP error and its message") {
            val httpClient =
                httpClient {
                    MockEngine {
                        respond(
                            """{"message":"cannot delete"}""",
                            status = HttpStatusCode.BadRequest,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                }

            KoniferClient(httpClient).assets("users/123").deleteAll() shouldBe
                KoniferResult.Failure.Http(400, "cannot delete")
        }

        test("delete retains the HTTP status when the error body is malformed") {
            val httpClient =
                httpClient {
                    MockEngine {
                        respond("not-json", status = HttpStatusCode.NotFound, headers = headersOf(HttpHeaders.ContentType, "text/plain"))
                    }
                }

            KoniferClient(httpClient).assets("users/123").entry(42).delete() shouldBe
                KoniferResult.Failure.Http(404, null)
        }

        test("delete returns a transport failure when the request fails") {
            val httpClient = httpClient { MockEngine { throw IOException("offline") } }

            val result = KoniferClient(httpClient).assets("users/123").deleteRecursively()

            (result is KoniferResult.Failure.Transport) shouldBe true
        }
    })
