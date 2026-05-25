package io.konifer.client.content

import io.konifer.client.ContentFetchMode
import io.konifer.client.KoniferClient
import io.konifer.client.KoniferResponse
import io.konifer.client.QuerySelectors
import io.konifer.client.harness.allTransformationsDsl
import io.konifer.client.harness.configureMockEngineError
import io.konifer.client.harness.createErrorResponse
import io.konifer.client.harness.httpClient
import io.konifer.client.requestedTransformation
import io.konifer.common.selector.Order
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.toByteArray
import kotlinx.coroutines.async

class KoniferClientContentTest :
    FunSpec({
        test("should be able to fetch content") {
            val imageBytes = readResourceBytes("/joshua-tree/joshua-tree.png")
            val httpClient =
                httpClient {
                    configureMockEngineHappy(
                        expectedPath = "/assets/users/123/-/content",
                        bytes = imageBytes,
                    )
                }

            val koniferClient = KoniferClient(httpClient)

            val responseChannel = ByteChannel()
            val actualBytes =
                async {
                    responseChannel.toByteArray()
                }
            val response =
                koniferClient.fetchAssetContent(
                    path = "/users/123",
                    byteChannel = responseChannel,
                    fetchMode = ContentFetchMode.CONTENT,
                )
            response::class shouldBe KoniferResponse.Success::class
            actualBytes.await() shouldBe imageBytes
        }

        test("should be able to fetch content bytes") {
            val imageBytes = readResourceBytes("/joshua-tree/joshua-tree.png")
            val httpClient =
                httpClient {
                    configureMockEngineHappy(
                        expectedPath = "/assets/users/123/-/content",
                        bytes = imageBytes,
                    )
                }

            val koniferClient = KoniferClient(httpClient)

            val response =
                koniferClient.fetchAssetContentBytes(
                    path = "/users/123",
                )
            response::class shouldBe KoniferResponse.Success::class
            (response as KoniferResponse.Success<*>).body shouldBe imageBytes
        }

        // Ensure ByteChannel works when content exceeds buffer size
        test("should be able to fetch large content bytes") {
            val imageBytes = readResourceBytes("/large/joshua-tree.jpeg")
            val httpClient =
                httpClient {
                    configureMockEngineHappy(
                        expectedPath = "/assets/users/123/-/content",
                        bytes = imageBytes,
                    )
                }

            val koniferClient = KoniferClient(httpClient)

            val response =
                koniferClient.fetchAssetContentBytes(
                    path = "/users/123",
                )
            response::class shouldBe KoniferResponse.Success::class
            (response as KoniferResponse.Success<*>).body shouldBe imageBytes
        }

        test("should be able to fetch content as redirect") {
            val imageBytes = readResourceBytes("/joshua-tree/joshua-tree.png")
            val httpClient =
                httpClient {
                    configureMockEngineHappyRedirect(
                        expectedPath = "/assets/users/123/-/content",
                        bytes = imageBytes,
                    )
                }

            val koniferClient = KoniferClient(httpClient)

            val responseChannel = ByteChannel()
            val actualBytes =
                async {
                    responseChannel.toByteArray()
                }
            val response =
                koniferClient.fetchAssetContent(
                    path = "/users/123",
                    byteChannel = responseChannel,
                    fetchMode = ContentFetchMode.CONTENT,
                )
            response::class shouldBe KoniferResponse.Success::class
            actualBytes.await() shouldBe imageBytes
        }

        test("should be able to fetch content bytes as redirect") {
            val imageBytes = readResourceBytes("/joshua-tree/joshua-tree.png")
            val httpClient =
                httpClient {
                    configureMockEngineHappyRedirect(
                        expectedPath = "/assets/users/123/-/redirect",
                        bytes = imageBytes,
                    )
                }

            val koniferClient = KoniferClient(httpClient)

            val response =
                koniferClient.fetchAssetContentBytes(
                    path = "/users/123",
                    fetchMode = ContentFetchMode.REDIRECT,
                )
            response::class shouldBe KoniferResponse.Success::class
            (response as KoniferResponse.Success<*>).body shouldBe imageBytes
        }

        test("should be able to fetch content with entryId selector") {
            val imageBytes = readResourceBytes("/joshua-tree/joshua-tree.png")
            val httpClient =
                httpClient {
                    configureMockEngineHappy(
                        expectedPath = "/assets/users/123/-/entry/1/content",
                        bytes = imageBytes,
                    )
                }

            val koniferClient = KoniferClient(httpClient)

            val responseChannel = ByteChannel()
            val actualBytes =
                async {
                    responseChannel.toByteArray()
                }
            val response =
                koniferClient.fetchAssetContent(
                    path = "/users/123",
                    byteChannel = responseChannel,
                    querySelectors = QuerySelectors.EntryId(1),
                    fetchMode = ContentFetchMode.CONTENT,
                )
            response::class shouldBe KoniferResponse.Success::class
            actualBytes.await() shouldBe imageBytes
        }

        test("should be able to fetch content with order selector") {
            val imageBytes = readResourceBytes("/joshua-tree/joshua-tree.png")
            val httpClient =
                httpClient {
                    configureMockEngineHappy(
                        expectedPath = "/assets/users/123/-/modified/content",
                        bytes = imageBytes,
                    )
                }

            val koniferClient = KoniferClient(httpClient)

            val responseChannel = ByteChannel()
            val actualBytes =
                async {
                    responseChannel.toByteArray()
                }
            val response =
                koniferClient.fetchAssetContent(
                    path = "/users/123",
                    byteChannel = responseChannel,
                    querySelectors = QuerySelectors.OrderBy(Order.MODIFIED),
                    fetchMode = ContentFetchMode.CONTENT,
                )
            response::class shouldBe KoniferResponse.Success::class
            actualBytes.await() shouldBe imageBytes
        }

        test("should return the error message on a client error") {
            val serverResponse = createErrorResponse("not found")
            val httpClient =
                httpClient {
                    configureMockEngineError(
                        expectedPath = "/assets/users/123/-/content",
                        response = serverResponse,
                        statusCode = HttpStatusCode.NotFound,
                    )
                }

            val koniferClient = KoniferClient(httpClient)

            val byteChannel = ByteChannel()
            val response =
                koniferClient.fetchAssetContent(
                    path = "/users/123",
                    byteChannel = byteChannel,
                    requestedTransformation = requestedTransformation {},
                    fetchMode = ContentFetchMode.CONTENT,
                )
            response::class shouldBe KoniferResponse.HttpError::class
            with(response as KoniferResponse.HttpError) {
                message shouldBe serverResponse.message
                httpStatusCode shouldBe HttpStatusCode.NotFound
            }
            byteChannel.isClosedForWrite shouldBe true
        }

        test("should return the error message when fetching content bytes on a client error") {
            val serverResponse = createErrorResponse("not found")
            val httpClient =
                httpClient {
                    configureMockEngineError(
                        expectedPath = "/assets/users/123/-/content",
                        response = serverResponse,
                        statusCode = HttpStatusCode.NotFound,
                    )
                }

            val koniferClient = KoniferClient(httpClient)

            val response =
                koniferClient.fetchAssetContentBytes(
                    path = "/users/123",
                    requestedTransformation = requestedTransformation {},
                )
            response::class shouldBe KoniferResponse.HttpError::class
            with(response as KoniferResponse.HttpError) {
                message shouldBe serverResponse.message
                httpStatusCode shouldBe HttpStatusCode.NotFound
            }
        }

        test("should properly translate requested transformation into query parameters") {
            val imageBytes = readResourceBytes("/joshua-tree/joshua-tree.png")
            val httpClient =
                httpClient {
                    configureMockEngineHappy(
                        expectedPath = "/assets/users/123/-/content",
                        bytes = imageBytes,
                        requestedTransformation = allTransformationsDsl,
                    )
                }

            val koniferClient = KoniferClient(httpClient)

            val responseChannel = ByteChannel()
            val actualBytes =
                async {
                    responseChannel.toByteArray()
                }
            val response =
                koniferClient.fetchAssetContent(
                    path = "/users/123",
                    byteChannel = responseChannel,
                    querySelectors = QuerySelectors.None(),
                    requestedTransformation = allTransformationsDsl,
                    fetchMode = ContentFetchMode.CONTENT,
                )
            response::class shouldBe KoniferResponse.Success::class
            actualBytes.await() shouldBe imageBytes
        }
    })
