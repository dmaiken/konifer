package io.konifer.clientV2.asset.content

import io.konifer.client.harness.assertLabels
import io.konifer.client.harness.assertRequestedTransformation
import io.konifer.client.harness.assertSignatureParameter
import io.konifer.client.harness.httpClient
import io.konifer.clientV2.KoniferClientV2
import io.konifer.clientV2.KoniferV2Result
import io.konifer.clientV2.assets.fetch.ContentDelivery
import io.konifer.clientV2.assets.fetch.requestedTransformation
import io.konifer.clientV2.internal.HmacSigningConfig
import io.konifer.clientV2.internal.KoniferUrlSigner
import io.konifer.common.http.ErrorResponse
import io.konifer.common.selector.Order
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.toByteArray
import kotlinx.coroutines.async
import kotlinx.io.IOException
import kotlinx.serialization.json.Json

class AssetContentTest :
    FunSpec({
        test("contentBytes fetches the newest variant by default") {
            val bytes = byteArrayOf(1, 2, 3, 4)
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        request.method shouldBe HttpMethod.Get
                        request.url.encodedPath shouldBe "/assets/users/123/-/new/content"
                        respond(bytes, headers = headersOf(HttpHeaders.ContentType, "image/png"))
                    }
                }

            KoniferClientV2(httpClient)
                .assets("/users/123")
                .variant(requestedTransformation {})
                .fetchContentBytes() shouldBe KoniferV2Result.Success(bytes)
        }

        test("contentBytes uses labels, order, and transformation") {
            val bytes = byteArrayOf(5, 6)
            val transformation = requestedTransformation { width = 80 }
            val labels = mapOf("Camera" to "phone", "format" to "display")
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        request.url.encodedPath shouldBe "/assets/users/123/-/modified/content"
                        assertLabels(request.url.parameters, labels)
                        assertRequestedTransformation(request.url.parameters, transformation)
                        respond(bytes, headers = headersOf(HttpHeaders.ContentType, "image/png"))
                    }
                }

            KoniferClientV2(httpClient)
                .assets("users/123")
                .matchingLabels(labels)
                .orderBy(Order.MODIFIED)
                .variant(transformation)
                .fetchContentBytes() shouldBe KoniferV2Result.Success(bytes)
        }

        test("contentBytes follows the delivery redirect with selectors, transformation, and signing") {
            val bytes = byteArrayOf(10, 11, 12)
            val labels = mapOf("Camera" to "phone")
            val transformation = requestedTransformation { width = 80 }
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        when (request.url.host) {
                            "delivery.example" -> {
                                request.url.encodedPath shouldBe "/variant.png"
                                assertSignatureParameter(request.url.parameters, false)
                                respond(bytes, headers = headersOf(HttpHeaders.ContentType, "image/png"))
                            }

                            else -> {
                                request.url.encodedPath shouldBe "/assets/users/123/-/modified/redirect"
                                assertLabels(request.url.parameters, labels)
                                assertRequestedTransformation(request.url.parameters, transformation)
                                assertSignatureParameter(request.url.parameters, true)
                                respond(
                                    "",
                                    status = HttpStatusCode.TemporaryRedirect,
                                    headers = headersOf(HttpHeaders.Location, "https://delivery.example/variant.png"),
                                )
                            }
                        }
                    }
                }
            val client = KoniferClientV2(httpClient, KoniferUrlSigner.create(HmacSigningConfig(secretKey = "secret")))

            client
                .assets("users/123")
                .matchingLabels(labels)
                .orderBy(Order.MODIFIED)
                .variant(transformation)
                .fetchContentBytes(ContentDelivery.FOLLOW_REDIRECT) shouldBe KoniferV2Result.Success(bytes)
        }

        test("writeContentTo streams an entry variant and closes the destination") {
            val bytes = ByteArray(100_000) { (it % 256).toByte() }
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        request.url.encodedPath shouldBe "/assets/users/123/-/entry/42/content"
                        respond(bytes, headers = headersOf(HttpHeaders.ContentType, "image/png"))
                    }
                }
            val destination = ByteChannel()
            val received = async { destination.toByteArray() }

            val result =
                KoniferClientV2(httpClient)
                    .assets("users/123")
                    .entry(42)
                    .variant(requestedTransformation {})
                    .fetchAndWriteContentTo(destination)

            result shouldBe KoniferV2Result.Success(Unit)
            received.await() shouldBe bytes
            destination.isClosedForWrite shouldBe true
        }

        test("writeContentTo includes labels, order, and transformation") {
            val bytes = byteArrayOf(7, 8, 9)
            val labels = mapOf("Camera" to "phone")
            val transformation = requestedTransformation { width = 80 }
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        request.url.encodedPath shouldBe "/assets/users/123/-/modified/content"
                        assertLabels(request.url.parameters, labels)
                        assertRequestedTransformation(request.url.parameters, transformation)
                        respond(bytes, headers = headersOf(HttpHeaders.ContentType, "image/png"))
                    }
                }
            val destination = ByteChannel()
            val received = async { destination.toByteArray() }

            KoniferClientV2(httpClient)
                .assets("users/123")
                .matchingLabels(labels)
                .orderBy(Order.MODIFIED)
                .variant(transformation)
                .fetchAndWriteContentTo(destination) shouldBe KoniferV2Result.Success(Unit)
            received.await() shouldBe bytes
        }

        test("writeContentTo follows a redirect even when the supplied client disables redirects") {
            val bytes = ByteArray(100_000) { (it % 256).toByte() }
            val httpClient =
                HttpClient(
                    MockEngine { request ->
                        when (request.url.host) {
                            "delivery.example" -> {
                                request.url.encodedPath shouldBe "/variant.png"
                                respond(bytes, headers = headersOf(HttpHeaders.ContentType, "image/png"))
                            }

                            else -> {
                                request.url.encodedPath shouldBe "/assets/users/123/-/entry/42/redirect"
                                respond(
                                    "",
                                    status = HttpStatusCode.TemporaryRedirect,
                                    headers = headersOf(HttpHeaders.Location, "https://delivery.example/variant.png"),
                                )
                            }
                        }
                    },
                ) {
                    followRedirects = false
                }
            val destination = ByteChannel()
            val received = async { destination.toByteArray() }

            val result =
                KoniferClientV2(httpClient)
                    .assets("users/123")
                    .entry(42)
                    .variant(requestedTransformation {})
                    .fetchAndWriteContentTo(destination, ContentDelivery.FOLLOW_REDIRECT)

            result shouldBe KoniferV2Result.Success(Unit)
            received.await() shouldBe bytes
            destination.isClosedForWrite shouldBe true
        }

        test("redirected content does not forward the service authorization header") {
            val bytes = byteArrayOf(3, 2, 1)
            val httpClient =
                HttpClient(
                    MockEngine { request ->
                        when (request.url.host) {
                            "delivery.example" -> {
                                request.headers[HttpHeaders.Authorization] shouldBe null
                                respond(bytes)
                            }

                            else -> {
                                request.headers[HttpHeaders.Authorization] shouldBe "Bearer service-secret"
                                respond(
                                    "",
                                    status = HttpStatusCode.TemporaryRedirect,
                                    headers = headersOf(HttpHeaders.Location, "https://delivery.example/variant.png"),
                                )
                            }
                        }
                    },
                ) {
                    defaultRequest { header(HttpHeaders.Authorization, "Bearer service-secret") }
                }

            KoniferClientV2(httpClient)
                .assets("users/123")
                .variant(requestedTransformation {})
                .fetchContentBytes(ContentDelivery.FOLLOW_REDIRECT) shouldBe KoniferV2Result.Success(bytes)
        }

        test("a delivery host error is returned and closes the streaming destination") {
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        when (request.url.host) {
                            "delivery.example" -> {
                                respond(
                                    "unavailable",
                                    status = HttpStatusCode.ServiceUnavailable,
                                    headers = headersOf(HttpHeaders.ContentType, "text/plain"),
                                )
                            }

                            else -> {
                                respond(
                                    "",
                                    status = HttpStatusCode.TemporaryRedirect,
                                    headers = headersOf(HttpHeaders.Location, "https://delivery.example/variant.png"),
                                )
                            }
                        }
                    }
                }
            val destination = ByteChannel()

            val result =
                KoniferClientV2(httpClient)
                    .assets("users/123")
                    .variant(requestedTransformation {})
                    .fetchAndWriteContentTo(destination, ContentDelivery.FOLLOW_REDIRECT)

            result shouldBe KoniferV2Result.Failure.Http(503, null)
            destination.isClosedForWrite shouldBe true
        }

        test("HTTP errors retain status and server message") {
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        request.url.encodedPath shouldBe "/assets/users/123/-/new/content"
                        respond(
                            Json.encodeToString(ErrorResponse("not found")),
                            status = HttpStatusCode.NotFound,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                }

            KoniferClientV2(httpClient)
                .assets("users/123")
                .variant(requestedTransformation {})
                .fetchContentBytes() shouldBe KoniferV2Result.Failure.Http(404, "not found")
        }

        test("writeContentTo closes the destination on an HTTP error") {
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
            val destination = ByteChannel()

            val result =
                KoniferClientV2(httpClient)
                    .assets("users/123")
                    .variant(requestedTransformation {})
                    .fetchAndWriteContentTo(destination)

            result shouldBe KoniferV2Result.Failure.Http(404, "not found")
            destination.isClosedForWrite shouldBe true
        }

        test("contentBytes signs a request for a specified entry") {
            val bytes = byteArrayOf(1, 2, 3)
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        request.url.encodedPath shouldBe "/assets/users/123/-/entry/42/content"
                        assertSignatureParameter(request.url.parameters, true)
                        respond(bytes, headers = headersOf(HttpHeaders.ContentType, "image/png"))
                    }
                }
            val client = KoniferClientV2(httpClient, KoniferUrlSigner.create(HmacSigningConfig(secretKey = "secret")))

            client
                .assets("users/123")
                .entry(42)
                .variant(requestedTransformation {})
                .fetchContentBytes() shouldBe
                KoniferV2Result.Success(bytes)
        }

        test("contentBytes returns a transport failure when the request fails") {
            val httpClient = httpClient { MockEngine { throw IOException("offline") } }

            val result = KoniferClientV2(httpClient).assets("users/123").variant(requestedTransformation {}).fetchContentBytes()

            (result is KoniferV2Result.Failure.Transport) shouldBe true
        }
    })
