package io.konifer.client.content

import io.konifer.client.KoniferClient
import io.konifer.client.KoniferResponse
import io.konifer.client.QuerySelectors
import io.konifer.client.configureMockEngineError
import io.konifer.client.createErrorResponse
import io.konifer.client.requestedTransformation
import io.konifer.common.image.Filter
import io.konifer.common.image.Fit
import io.konifer.common.image.Flip
import io.konifer.common.image.Gravity
import io.konifer.common.image.ImageFormat
import io.konifer.common.image.Rotate
import io.konifer.common.selector.Order
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.toByteArray
import kotlinx.coroutines.async
import kotlinx.serialization.json.Json

class KoniferClientContentTest :
    FunSpec({
        test("should be able to fetch content") {
            val imageBytes = readResourceBytes("/joshua-tree/joshua-tree.png")
            val mockEngine =
                configureMockEngineHappy(
                    expectedPath = "/assets/users/123/-/content",
                    bytes = imageBytes,
                )
            val httpClient =
                HttpClient(mockEngine) {
                    install(ContentNegotiation) {
                        json(Json)
                    }
                }

            val koniferClient = KoniferClient(httpClient)

            val responseChannel = ByteChannel()
            val actualBytes =
                async {
                    responseChannel.toByteArray()
                }
            val response =
                koniferClient.getAssetContent(
                    path = "/users/123",
                    byteChannel = responseChannel,
                    requestedTransformation = requestedTransformation {},
                )
            response::class shouldBe KoniferResponse.Success::class
            actualBytes.await() shouldBe imageBytes
        }

        test("should be able to fetch content with entryId selector") {
            val imageBytes = readResourceBytes("/joshua-tree/joshua-tree.png")
            val mockEngine =
                configureMockEngineHappy(
                    expectedPath = "/assets/users/123/-/content/entry/1",
                    bytes = imageBytes,
                )
            val httpClient =
                HttpClient(mockEngine) {
                    install(ContentNegotiation) {
                        json(Json)
                    }
                }

            val koniferClient = KoniferClient(httpClient)

            val responseChannel = ByteChannel()
            val actualBytes =
                async {
                    responseChannel.toByteArray()
                }
            val response =
                koniferClient.getAssetContent(
                    path = "/users/123",
                    byteChannel = responseChannel,
                    querySelectors = QuerySelectors.EntryId(1),
                    requestedTransformation = requestedTransformation {},
                )
            response::class shouldBe KoniferResponse.Success::class
            actualBytes.await() shouldBe imageBytes
        }

        test("should be able to fetch content with order selector") {
            val imageBytes = readResourceBytes("/joshua-tree/joshua-tree.png")
            val mockEngine =
                configureMockEngineHappy(
                    expectedPath = "/assets/users/123/-/content/modified",
                    bytes = imageBytes,
                )
            val httpClient =
                HttpClient(mockEngine) {
                    install(ContentNegotiation) {
                        json(Json)
                    }
                }

            val koniferClient = KoniferClient(httpClient)

            val responseChannel = ByteChannel()
            val actualBytes =
                async {
                    responseChannel.toByteArray()
                }
            val response =
                koniferClient.getAssetContent(
                    path = "/users/123",
                    byteChannel = responseChannel,
                    querySelectors = QuerySelectors.OrderBy(Order.MODIFIED),
                    requestedTransformation = requestedTransformation {},
                )
            response::class shouldBe KoniferResponse.Success::class
            actualBytes.await() shouldBe imageBytes
        }

        test("should return the error message on a client error") {
            val serverResponse = createErrorResponse("not found")
            val mockEngine =
                configureMockEngineError(
                    expectedPath = "/assets/users/123/-/content",
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

            val byteChannel = ByteChannel()
            val response =
                koniferClient.getAssetContent(
                    path = "/users/123",
                    byteChannel = byteChannel,
                    requestedTransformation = requestedTransformation {},
                )
            response::class shouldBe KoniferResponse.HttpError::class
            (response as KoniferResponse.HttpError).message shouldBe serverResponse.message
            byteChannel.isClosedForWrite shouldBe true
        }

        test("should properly translate requested transformation into query parameters") {
            val imageBytes = readResourceBytes("/joshua-tree/joshua-tree.png")
            val requestedTransformation =
                requestedTransformation {
                    height(10)
                    width(5)
                    fit(Fit.FIT)
                    filter(Filter.BLACK_WHITE)
                    flip(Flip.H)
                    blur(100)
                    gravity(Gravity.CENTER)
                    format(ImageFormat.GIF)
                    rotate(Rotate.NINETY)
                    quality(55)
                    pad(25)
                    padColor("#123456")
                    profile("profile")
                }
            val mockEngine =
                configureMockEngineHappy(
                    expectedPath = "/assets/users/123/-/content",
                    bytes = imageBytes,
                    requestedTransformation = requestedTransformation,
                )
            val httpClient =
                HttpClient(mockEngine) {
                    install(ContentNegotiation) {
                        json(Json)
                    }
                }

            val koniferClient = KoniferClient(httpClient)

            val responseChannel = ByteChannel()
            val actualBytes =
                async {
                    responseChannel.toByteArray()
                }
            val response =
                koniferClient.getAssetContent(
                    path = "/users/123",
                    byteChannel = responseChannel,
                    querySelectors = QuerySelectors.None(),
                    requestedTransformation = requestedTransformation,
                )
            response::class shouldBe KoniferResponse.Success::class
            actualBytes.await() shouldBe imageBytes
        }
    })
