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
import io.ktor.http.HttpStatusCode

class KoniferClientLimitInfoTest :
    FunSpec({

        test("should be able to fetch asset info with limit") {
            val serverResponse =
                listOf(
                    createInfoResponse(),
                    createInfoResponse(),
                )
            val httpClient =
                httpClient {
                    configureMockEngineHappy(
                        expectedPath = "/assets/users/123/-/info",
                        response = serverResponse,
                    )
                }

            val koniferClient = KoniferClient(httpClient)

            val response =
                koniferClient.fetchAssetInfo(
                    path = "/users/123",
                    limit = 2,
                )
            response::class shouldBe KoniferResponse.Success::class
            (response as KoniferResponse.Success<*>).body shouldBe serverResponse
        }

        test("should add signature parameter when fetching asset info with limit with a signed client") {
            val serverResponse =
                listOf(
                    createInfoResponse(),
                    createInfoResponse(),
                )
            val httpClient =
                httpClient {
                    configureMockEngineHappy(
                        expectedPath = "/assets/users/123/-/info",
                        response = serverResponse,
                        expectSignature = true,
                    )
                }

            val koniferClient = signedKoniferClient(httpClient)

            val response =
                koniferClient.fetchAssetInfo(
                    path = "/users/123",
                    limit = 2,
                )

            response::class shouldBe KoniferResponse.Success::class
        }

        test("should be able to fetch asset info with order selector") {
            val serverResponse =
                listOf(
                    createInfoResponse(),
                    createInfoResponse(),
                )
            val httpClient =
                httpClient {
                    configureMockEngineHappy(
                        expectedPath = "/assets/users/123/-/new/info",
                        response = serverResponse,
                    )
                }

            val koniferClient = KoniferClient(httpClient)

            val response =
                koniferClient.fetchAssetInfo(
                    path = "/users/123",
                    querySelectors = OrderBy(Order.NEW),
                    limit = 2,
                )
            response::class shouldBe KoniferResponse.Success::class
            (response as KoniferResponse.Success<*>).body shouldBe serverResponse
        }

        test("should be able to fetch asset info with entryId selector") {
            val serverResponse =
                listOf(
                    createInfoResponse(),
                    createInfoResponse(),
                )
            val httpClient =
                httpClient {
                    configureMockEngineHappy(
                        expectedPath = "/assets/users/123/-/entry/1/info",
                        response = serverResponse,
                    )
                }

            val koniferClient = KoniferClient(httpClient)

            val response =
                koniferClient.fetchAssetInfo(
                    path = "/users/123",
                    querySelectors = EntryId(1),
                    limit = 2,
                )
            response::class shouldBe KoniferResponse.Success::class
            (response as KoniferResponse.Success<*>).body shouldBe serverResponse
        }

        test("should be able to fetch asset info with limit and labels") {
            val serverResponse =
                listOf(
                    createInfoResponse(),
                    createInfoResponse(),
                )
            val labels =
                mapOf(
                    "Album" to "vacation",
                    "limit" to "important",
                )
            val httpClient =
                httpClient {
                    configureMockEngineHappy(
                        expectedPath = "/assets/users/123/-/info",
                        response = serverResponse,
                        labels = labels,
                    )
                }

            val koniferClient = KoniferClient(httpClient)

            val response =
                koniferClient.fetchAssetInfo(
                    path = "/users/123",
                    limit = 2,
                    labels = labels,
                )
            response::class shouldBe KoniferResponse.Success::class
            (response as KoniferResponse.Success<*>).body shouldBe serverResponse
        }

        test("should return the error message on a client error") {
            val serverResponse = createErrorResponse("not found")
            val httpClient =
                httpClient {
                    configureMockEngineError(
                        expectedPath = "/assets/users/123/-/info",
                        response = serverResponse,
                        statusCode = HttpStatusCode.NotFound,
                    )
                }

            val koniferClient = KoniferClient(httpClient)

            val response =
                koniferClient.fetchAssetInfo(
                    path = "/users/123",
                    limit = 2,
                )
            response::class shouldBe KoniferResponse.HttpError::class
            with(response as KoniferResponse.HttpError) {
                message shouldBe serverResponse.message
                httpStatusCode shouldBe HttpStatusCode.NotFound.value
            }
        }
    })
