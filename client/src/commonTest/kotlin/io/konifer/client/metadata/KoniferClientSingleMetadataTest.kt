package io.konifer.client.metadata

import io.konifer.client.KoniferClient
import io.konifer.client.KoniferResponse
import io.konifer.client.QuerySelectors
import io.konifer.client.harness.configureMockEngineError
import io.konifer.client.harness.createErrorResponse
import io.konifer.common.selector.Order
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class KoniferClientSingleMetadataTest :
    FunSpec({

        test("should be able to fetch asset metadata") {
            val serverResponse = createMetadataResponse()
            val mockEngine =
                configureMockEngineHappy(
                    expectedPath = "/assets/users/123/-/metadata",
                    response = serverResponse,
                )
            val httpClient =
                HttpClient(mockEngine) {
                    install(ContentNegotiation) {
                        json(Json)
                    }
                }

            val koniferClient = KoniferClient(httpClient)

            val response = koniferClient.getAssetMetadata("/users/123")
            response::class shouldBe KoniferResponse.Success::class
            (response as KoniferResponse.Success<*>).body shouldBe serverResponse
        }

        test("should be able to fetch asset metadata with order selector") {
            val serverResponse = createMetadataResponse()
            val mockEngine =
                configureMockEngineHappy(
                    expectedPath = "/assets/users/123/-/metadata/new",
                    response = serverResponse,
                )
            val httpClient =
                HttpClient(mockEngine) {
                    install(ContentNegotiation) {
                        json(Json)
                    }
                }

            val koniferClient = KoniferClient(httpClient)

            val response = koniferClient.getAssetMetadata("/users/123", QuerySelectors.OrderBy(Order.NEW))
            response::class shouldBe KoniferResponse.Success::class
            (response as KoniferResponse.Success<*>).body shouldBe serverResponse
        }

        test("should be able to fetch asset metadata with entryId selector") {
            val serverResponse = createMetadataResponse()
            val mockEngine =
                configureMockEngineHappy(
                    expectedPath = "/assets/users/123/-/metadata/entry/1",
                    response = serverResponse,
                )
            val httpClient =
                HttpClient(mockEngine) {
                    install(ContentNegotiation) {
                        json(Json)
                    }
                }

            val koniferClient = KoniferClient(httpClient)

            val response = koniferClient.getAssetMetadata("/users/123", QuerySelectors.EntryId(1))
            response::class shouldBe KoniferResponse.Success::class
            (response as KoniferResponse.Success<*>).body shouldBe serverResponse
        }

        test("should return the error message on a client error") {
            val serverResponse = createErrorResponse("not found")
            val mockEngine =
                configureMockEngineError(
                    expectedPath = "/assets/users/123/-/metadata",
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

            val response = koniferClient.getAssetMetadata("/users/123")
            response::class shouldBe KoniferResponse.HttpError::class
            (response as KoniferResponse.HttpError).message shouldBe serverResponse.message
        }
    })
