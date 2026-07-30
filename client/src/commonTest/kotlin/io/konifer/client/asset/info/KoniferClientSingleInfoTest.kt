package io.konifer.client.asset.info

import io.konifer.client.EntryId
import io.konifer.client.KoniferClient
import io.konifer.client.KoniferResponse
import io.konifer.client.OrderBy
import io.konifer.client.harness.configureMockEngineError
import io.konifer.client.harness.createErrorResponse
import io.konifer.client.harness.httpClient
import io.konifer.client.harness.signedKoniferClient
import io.konifer.common.selector.Order
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class KoniferClientSingleInfoTest :
    FunSpec({

        test("should be able to fetch asset info") {
            val serverResponse = createInfoResponse()
            val mockEngine =
                configureMockEngineHappy(
                    expectedPath = "/assets/users/123/-/info",
                    response = serverResponse,
                )
            val httpClient =
                HttpClient(mockEngine) {
                    install(ContentNegotiation) {
                        json(Json)
                    }
                }

            val koniferClient = KoniferClient(httpClient)

            val response = koniferClient.fetchAssetInfo("/users/123")
            response::class shouldBe KoniferResponse.Success::class
            (response as KoniferResponse.Success<*>).body shouldBe serverResponse
        }

        test("should add signature parameter when fetching asset info with a signed client") {
            val serverResponse = createInfoResponse()
            val httpClient =
                httpClient {
                    configureMockEngineHappy(
                        expectedPath = "/assets/users/123/-/info",
                        response = serverResponse,
                        expectSignature = true,
                    )
                }

            val koniferClient = signedKoniferClient(httpClient)

            val response = koniferClient.fetchAssetInfo("/users/123")

            response::class shouldBe KoniferResponse.Success::class
        }

        test("should be able to fetch asset info with order selector") {
            val serverResponse = createInfoResponse()
            val mockEngine =
                configureMockEngineHappy(
                    expectedPath = "/assets/users/123/-/new/info",
                    response = serverResponse,
                )
            val httpClient =
                HttpClient(mockEngine) {
                    install(ContentNegotiation) {
                        json(Json)
                    }
                }

            val koniferClient = KoniferClient(httpClient)

            val response = koniferClient.fetchAssetInfo("/users/123", OrderBy(Order.NEW))
            response::class shouldBe KoniferResponse.Success::class
            (response as KoniferResponse.Success<*>).body shouldBe serverResponse
        }

        test("should be able to fetch asset info with entryId selector") {
            val serverResponse = createInfoResponse()
            val mockEngine =
                configureMockEngineHappy(
                    expectedPath = "/assets/users/123/-/entry/1/info",
                    response = serverResponse,
                )
            val httpClient =
                HttpClient(mockEngine) {
                    install(ContentNegotiation) {
                        json(Json)
                    }
                }

            val koniferClient = KoniferClient(httpClient)

            val response = koniferClient.fetchAssetInfo("/users/123", EntryId(1))
            response::class shouldBe KoniferResponse.Success::class
            (response as KoniferResponse.Success<*>).body shouldBe serverResponse
        }

        test("should be able to fetch asset info with labels") {
            val serverResponse = createInfoResponse()
            val labels =
                mapOf(
                    "Camera" to "iphone",
                    "fit" to "profile",
                )
            val mockEngine =
                configureMockEngineHappy(
                    expectedPath = "/assets/users/123/-/info",
                    response = serverResponse,
                    labels = labels,
                )
            val httpClient =
                HttpClient(mockEngine) {
                    install(ContentNegotiation) {
                        json(Json)
                    }
                }

            val koniferClient = KoniferClient(httpClient)

            val response =
                koniferClient.fetchAssetInfo(
                    path = "/users/123",
                    labels = labels,
                )
            response::class shouldBe KoniferResponse.Success::class
            (response as KoniferResponse.Success<*>).body shouldBe serverResponse
        }

        test("should return the error message on a client error") {
            val serverResponse = createErrorResponse("not found")
            val mockEngine =
                configureMockEngineError(
                    expectedPath = "/assets/users/123/-/info",
                    response = serverResponse,
                    statusCode = HttpStatusCode.NotFound,
                )
            val httpClient =
                HttpClient(mockEngine) {
                    install(ContentNegotiation) {
                        json(Json)
                    }
                }

            val koniferClient = KoniferClient(httpClient)

            val response = koniferClient.fetchAssetInfo("/users/123")
            response::class shouldBe KoniferResponse.HttpError::class
            with(response as KoniferResponse.HttpError) {
                message shouldBe serverResponse.message
                httpStatusCode shouldBe HttpStatusCode.NotFound.value
            }
        }
    })
