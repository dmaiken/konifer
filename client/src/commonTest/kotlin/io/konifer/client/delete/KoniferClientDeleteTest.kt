package io.konifer.client.delete

import io.konifer.client.EntryId
import io.konifer.client.KoniferClient
import io.konifer.client.KoniferResponse
import io.konifer.client.OrderBy
import io.konifer.client.Recursive
import io.konifer.client.harness.httpClient
import io.konifer.common.selector.Order
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode

class KoniferClientDeleteTest :
    FunSpec({

        test("should be able to delete a single asset") {
            val httpClient =
                httpClient {
                    configureMockEngineHappy(
                        expectedPath = "/assets/users/123",
                    )
                }
            val koniferClient = KoniferClient(httpClient)

            koniferClient.deleteAsset(
                path = "/users/123",
            )::class shouldBe KoniferResponse.Success::class
        }

        test("should be able to delete recursively") {
            val httpClient =
                httpClient {
                    configureMockEngineHappy(
                        expectedPath = "/assets/users/123/-/recursive",
                    )
                }
            val koniferClient = KoniferClient(httpClient)

            koniferClient.deleteAsset(
                path = "/users/123",
                querySelectors = Recursive(),
            )::class shouldBe KoniferResponse.Success::class
        }

        test("should be able to delete by entryId") {
            val httpClient =
                httpClient {
                    configureMockEngineHappy(
                        expectedPath = "/assets/users/123/-/entry/1",
                    )
                }
            val koniferClient = KoniferClient(httpClient)

            koniferClient.deleteAsset(
                path = "/users/123",
                querySelectors = EntryId(1),
            )::class shouldBe KoniferResponse.Success::class
        }

        test("should be able to delete with ordering modifier") {
            val httpClient =
                httpClient {
                    configureMockEngineHappy(
                        expectedPath = "/assets/users/123/-/modified",
                    )
                }
            val koniferClient = KoniferClient(httpClient)

            koniferClient.deleteAsset(
                path = "/users/123",
                querySelectors = OrderBy(Order.MODIFIED),
            )::class shouldBe KoniferResponse.Success::class
        }

        test("should be able to delete with limit") {
            val httpClient =
                httpClient {
                    configureMockEngineHappy(
                        expectedPath = "/assets/users/123",
                        limit = 10,
                    )
                }
            val koniferClient = KoniferClient(httpClient)

            koniferClient.deleteAsset(
                path = "/users/123",
                limit = 10,
            )::class shouldBe KoniferResponse.Success::class
        }

        test("should be able to delete with labels") {
            val httpClient =
                httpClient {
                    configureMockEngineHappy(
                        expectedPath = "/assets/users/123",
                    )
                }
            val koniferClient = KoniferClient(httpClient)

            koniferClient.deleteAsset(
                path = "/users/123",
                labels = mapOf("key" to "value", "w" to "100"),
            )::class shouldBe KoniferResponse.Success::class
        }

        test("should be able to delete with labels, limit and order modifier") {
            val httpClient =
                httpClient {
                    configureMockEngineHappy(
                        expectedPath = "/assets/users/123/-/modified",
                        limit = 10,
                    )
                }
            val koniferClient = KoniferClient(httpClient)

            koniferClient.deleteAsset(
                path = "/users/123",
                labels = mapOf("key" to "value", "w" to "100"),
                limit = 10,
                querySelectors = OrderBy(Order.MODIFIED),
            )::class shouldBe KoniferResponse.Success::class
        }

        test("should be able to delete with limit and a label named limit") {
            val httpClient =
                httpClient {
                    configureMockEngineHappy(
                        expectedPath = "/assets/users/123",
                        limit = 10,
                    )
                }
            val koniferClient = KoniferClient(httpClient)

            koniferClient.deleteAsset(
                path = "/users/123",
                labels = mapOf("key" to "value", "limit" to "100"),
                limit = 10,
            )::class shouldBe KoniferResponse.Success::class
        }

        test("error is mapped correctly") {
            val httpClient =
                httpClient {
                    configureMockEngineHappy(
                        expectedPath = "/assets/users/123",
                        limit = 10,
                        statusCode = HttpStatusCode.BadRequest,
                    )
                }
            val koniferClient = KoniferClient(httpClient)

            val response =
                koniferClient.deleteAsset(
                    path = "/users/123",
                    labels = mapOf("key" to "value", "limit" to "100"),
                    limit = 10,
                )

            response::class shouldBe KoniferResponse.HttpError::class
            (response as KoniferResponse.HttpError).httpStatusCode shouldBe HttpStatusCode.BadRequest.value
        }
    })
