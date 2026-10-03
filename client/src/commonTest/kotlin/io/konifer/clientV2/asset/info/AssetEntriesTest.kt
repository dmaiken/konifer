package io.konifer.clientV2.asset.info

import io.konifer.client.asset.info.createInfoResponse
import io.konifer.client.harness.assertLabels
import io.konifer.client.harness.assertSignatureParameter
import io.konifer.client.harness.httpClient
import io.konifer.clientV2.KoniferClientV2
import io.konifer.clientV2.KoniferV2Result
import io.konifer.clientV2.internal.HmacSigningConfig
import io.konifer.clientV2.internal.KoniferUrlSigner
import io.konifer.common.http.AssetEntriesResponse
import io.konifer.common.http.ErrorResponse
import io.konifer.common.selector.Order
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.io.IOException

class AssetEntriesTest :
    FunSpec({
        test("entries without a limit sends the client default of twenty") {
            val info = createInfoResponse()
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        request.url.encodedPath shouldBe "/assets/users/123/-/new/entries"
                        request.url.parameters["limit"] shouldBe "20"
                        respond(
                            Json.encodeToString(AssetEntriesResponse(listOf(info))),
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                }

            KoniferClientV2(httpClient).assets("users/123").fetchEntries() shouldBe
                KoniferV2Result.Success(AssetEntriesResponse(listOf(info)))
        }

        test("relative selection retains labels and ordering with the default limit") {
            val labels = mapOf("camera" to "phone")
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        request.url.encodedPath shouldBe "/assets/users/123/-/modified/entries"
                        request.url.parameters["limit"] shouldBe "20"
                        assertLabels(request.url.parameters, labels)
                        respond(
                            Json.encodeToString(AssetEntriesResponse(emptyList())),
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                }

            KoniferClientV2(httpClient)
                .assets("users/123")
                .matchingLabels(labels)
                .orderBy(Order.MODIFIED)
                .fetchEntries() shouldBe KoniferV2Result.Success(AssetEntriesResponse(emptyList()))
        }

        test("entries at a path fetches the newest entry in a collection envelope") {
            val info = createInfoResponse()
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        request.method shouldBe HttpMethod.Get
                        request.url.encodedPath shouldBe "/assets/users/123/-/new/entries"
                        request.url.parameters["limit"] shouldBe "1"
                        request.headers[HttpHeaders.Accept] shouldBe "application/json"
                        respond(
                            Json.encodeToString(AssetEntriesResponse(listOf(info))),
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                }

            KoniferClientV2(httpClient).assets("/users/123").fetchEntries(1) shouldBe
                KoniferV2Result.Success(AssetEntriesResponse(listOf(info)))
        }

        test("entries includes ordering, labels, and limit") {
            val info = listOf(createInfoResponse(), createInfoResponse())
            val labels = mapOf("Camera" to "phone", "limit" to "important")
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        request.url.encodedPath shouldBe "/assets/users/123/-/modified/entries"
                        request.url.parameters["limit"] shouldBe "2"
                        assertLabels(request.url.parameters, labels)
                        respond(
                            Json.encodeToString(AssetEntriesResponse(info)),
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                }

            KoniferClientV2(httpClient)
                .assets("users/123")
                .matchingLabels(labels)
                .orderBy(Order.MODIFIED)
                .fetchEntries(2) shouldBe KoniferV2Result.Success(AssetEntriesResponse(info))
        }

        test("entries returns an empty list when the collection is empty") {
            val httpClient =
                httpClient {
                    MockEngine {
                        respond(
                            Json.encodeToString(AssetEntriesResponse(emptyList())),
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                }

            KoniferClientV2(httpClient).assets("users/123").fetchEntries(1) shouldBe
                KoniferV2Result.Success(AssetEntriesResponse(emptyList()))
        }

        test("entries signs the complete URL when the client is configured to sign fetches") {
            val info = createInfoResponse()
            val labels = mapOf("camera" to "phone")
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        request.url.encodedPath shouldBe "/assets/users/123/-/new/entries"
                        request.url.parameters["limit"] shouldBe "3"
                        assertLabels(request.url.parameters, labels)
                        assertSignatureParameter(request.url.parameters, true)
                        respond(
                            Json.encodeToString(AssetEntriesResponse(listOf(info))),
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                }
            val client = KoniferClientV2(httpClient, KoniferUrlSigner.create(HmacSigningConfig(secretKey = "secret")))

            client.assets("users/123").matchingLabels(labels).fetchEntries(3) shouldBe
                KoniferV2Result.Success(AssetEntriesResponse(listOf(info)))
        }

        test("entries requires a positive limit before making a request") {
            val httpClient = httpClient { MockEngine { error("No request expected") } }

            shouldThrow<IllegalArgumentException> { KoniferClientV2(httpClient).assets("users/123").fetchEntries(0) }
            shouldThrow<IllegalArgumentException> { KoniferClientV2(httpClient).assets("users/123").fetchEntries(-1) }
        }

        test("entries maps HTTP failures and invalid success bodies") {
            val errorClient =
                httpClient {
                    MockEngine {
                        respond(
                            Json.encodeToString(ErrorResponse("unavailable")),
                            status = HttpStatusCode.BadRequest,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                }
            KoniferClientV2(errorClient).assets("users/123").fetchEntries(2) shouldBe
                KoniferV2Result.Failure.Http(400, "unavailable")

            val malformedClient =
                httpClient {
                    MockEngine { respond("{}", headers = headersOf(HttpHeaders.ContentType, "application/json")) }
                }
            val malformed = KoniferClientV2(malformedClient).assets("users/123").fetchEntries(2)
            (malformed is KoniferV2Result.Failure.InvalidResponse) shouldBe true
        }

        test("entries maps transport failures") {
            val httpClient = httpClient { MockEngine { throw IOException("offline") } }

            val result = KoniferClientV2(httpClient).assets("users/123").fetchEntries(2)
            (result is KoniferV2Result.Failure.Transport) shouldBe true
        }
    })
