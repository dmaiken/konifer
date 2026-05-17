package io.konifer.client

import io.konifer.client.content.configureMockEngineHappy
import io.konifer.client.content.configureMockEngineHappyRedirect
import io.konifer.client.content.readResourceBytes
import io.konifer.client.harness.configureMockEngineError
import io.konifer.client.harness.createErrorResponse
import io.konifer.client.harness.httpClient
import io.konifer.client.link.createLinkResponse
import io.konifer.client.metadata.createMetadataResponse
import io.konifer.client.store.configureMockMultipartEngineHappy
import io.konifer.client.store.configureMockUrlEngineHappy
import io.konifer.common.http.StoreAssetRequest
import io.konifer.common.image.Filter
import io.konifer.common.image.Fit
import io.konifer.common.image.ImageFormat
import io.konifer.common.selector.Order
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import io.ktor.http.HttpStatusCode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.coroutines.cancellation.CancellationException
import io.konifer.client.link.configureMockEngineHappy as configureMockLinkEngineHappy
import io.konifer.client.metadata.configureMockEngineHappy as configureMockMetadataEngineHappy
import io.konifer.client.redirect.configureMockEngineHappyRedirect as configureMockRedirectLocationEngineHappy
import io.konifer.client.update.configureMockEngineHappy as configureMockUpdateEngineHappy

class KoniferBlockingClientTest :
    FunSpec({
        test("should fetch single asset metadata") {
            val expectedResponse = createMetadataResponse()
            val httpClient =
                httpClient {
                    configureMockMetadataEngineHappy(
                        expectedPath = "/assets/users/123/-/metadata/modified",
                        response = expectedResponse,
                    )
                }
            val blockingClient = KoniferBlockingClient(KoniferClient(httpClient))

            val response =
                blockingClient.getAssetMetadata(
                    path = "/users/123",
                    querySelectors = QuerySelectors.OrderBy(Order.MODIFIED),
                )

            response::class shouldBe KoniferResponse.Success::class
            (response as KoniferResponse.Success<*>).body shouldBe expectedResponse
        }

        test("should fetch limited asset metadata") {
            val expectedResponse = listOf(createMetadataResponse(), createMetadataResponse())
            val httpClient =
                httpClient {
                    configureMockMetadataEngineHappy(
                        expectedPath = "/assets/users/123/-/metadata",
                        response = expectedResponse,
                    )
                }
            val blockingClient = KoniferBlockingClient(KoniferClient(httpClient))

            val response =
                blockingClient.getAssetMetadata(
                    path = "/users/123",
                    limit = expectedResponse.size,
                )

            response::class shouldBe KoniferResponse.Success::class
            (response as KoniferResponse.Success<*>).body shouldBe expectedResponse
        }

        test("should fetch content bytes using request options") {
            val imageBytes = readResourceBytes("/joshua-tree/joshua-tree.png")
            val requestedTransformation =
                RequestedTransformation
                    .Builder()
                    .height(10)
                    .width(5)
                    .fit(Fit.FIT)
                    .filter(Filter.BLACK_WHITE)
                    .build()
            val httpClient =
                httpClient {
                    configureMockEngineHappyRedirect(
                        expectedPath = "/assets/users/123/-/redirect/entry/1",
                        bytes = imageBytes,
                        requestedTransformation = requestedTransformation,
                    )
                }
            val blockingClient = KoniferBlockingClient(KoniferClient(httpClient))
            val options =
                AssetContentRequestOptions
                    .Builder()
                    .querySelectors(QuerySelectors.EntryId(1))
                    .requestedTransformation(requestedTransformation)
                    .fetchMode(ContentFetchMode.REDIRECT)
                    .build()

            val response =
                blockingClient.getAssetContentBytes(
                    path = "/users/123",
                    options = options,
                )

            response::class shouldBe KoniferResponse.Success::class
            (response as KoniferResponse.Success<*>).body shouldBe imageBytes
        }

        test("should write content to output stream") {
            val imageBytes = readResourceBytes("/joshua-tree/joshua-tree.png")
            val httpClient =
                httpClient {
                    configureMockEngineHappy(
                        expectedPath = "/assets/users/123/-/content",
                        bytes = imageBytes,
                    )
                }
            val blockingClient = KoniferBlockingClient(KoniferClient(httpClient))
            val outputStream = ByteArrayOutputStream()

            val response =
                blockingClient.getAssetContent(
                    path = "/users/123",
                    outputStream = outputStream,
                    options = AssetContentRequestOptions.Builder().build(),
                )

            response::class shouldBe KoniferResponse.Success::class
            outputStream.toByteArray() shouldBe imageBytes
        }

        test("should fetch redirect location") {
            val redirectLocation = "https://cdn.konifer.io/assets/users/123.png"
            val requestedTransformation =
                RequestedTransformation
                    .Builder()
                    .format(ImageFormat.WEBP)
                    .build()
            val httpClient =
                httpClient {
                    configureMockRedirectLocationEngineHappy(
                        expectedPath = "/assets/users/123/-/redirect/entry/7",
                        redirectLocation = redirectLocation,
                        requestedTransformation = requestedTransformation,
                    )
                }
            val blockingClient = KoniferBlockingClient(KoniferClient(httpClient))

            val response =
                blockingClient.getAssetRedirectLocation(
                    path = "/users/123",
                    querySelectors = QuerySelectors.EntryId(7),
                    requestedTransformation = requestedTransformation,
                )

            response::class shouldBe KoniferResponse.Success::class
            (response as KoniferResponse.Success<*>).body shouldBe redirectLocation
        }

        test("should fetch asset link") {
            val expectedResponse = createLinkResponse()
            val requestedTransformation =
                RequestedTransformation
                    .Builder()
                    .width(100)
                    .build()
            val httpClient =
                httpClient {
                    configureMockLinkEngineHappy(
                        expectedPath = "/assets/users/123/-/link",
                        response = expectedResponse,
                        requestedTransformation = requestedTransformation,
                    )
                }
            val blockingClient = KoniferBlockingClient(KoniferClient(httpClient))

            val response =
                blockingClient.getAssetLink(
                    path = "/users/123",
                    requestedTransformation = requestedTransformation,
                )

            response::class shouldBe KoniferResponse.Success::class
            (response as KoniferResponse.Success<*>).body shouldBe expectedResponse
        }

        test("should store asset from input stream") {
            val imageBytes = readResourceBytes("/joshua-tree/joshua-tree.png")
            val request = StoreAssetRequest(alt = "an image")
            val expectedResponse = createMetadataResponse()
            val httpClient =
                httpClient {
                    configureMockMultipartEngineHappy(
                        expectedPath = "/assets/users/123",
                        request = request,
                        assetBytes = imageBytes,
                        response = expectedResponse,
                    )
                }
            val blockingClient = KoniferBlockingClient(KoniferClient(httpClient))

            val response =
                blockingClient.storeAsset(
                    path = "/users/123",
                    format = ImageFormat.PNG,
                    request = request,
                    inputStream = ByteArrayInputStream(imageBytes),
                )

            response::class shouldBe KoniferResponse.Success::class
            (response as KoniferResponse.Success<*>).body shouldBe expectedResponse
        }

        test("should store asset from bytes") {
            val imageBytes = readResourceBytes("/joshua-tree/joshua-tree.png")
            val request = StoreAssetRequest(alt = "an image")
            val expectedResponse = createMetadataResponse()
            val httpClient =
                httpClient {
                    configureMockMultipartEngineHappy(
                        expectedPath = "/assets/users/123",
                        request = request,
                        assetBytes = imageBytes,
                        response = expectedResponse,
                    )
                }
            val blockingClient = KoniferBlockingClient(KoniferClient(httpClient))

            val response =
                blockingClient.storeAsset(
                    path = "/users/123",
                    format = ImageFormat.PNG,
                    request = request,
                    bytes = imageBytes,
                )

            response::class shouldBe KoniferResponse.Success::class
            (response as KoniferResponse.Success<*>).body shouldBe expectedResponse
        }

        test("should store asset from url request") {
            val request = StoreAssetRequest(url = "https://localhost/image.jpg")
            val expectedResponse = createMetadataResponse()
            val httpClient =
                httpClient {
                    configureMockUrlEngineHappy(
                        expectedPath = "/assets/users/123",
                        request = request,
                        response = expectedResponse,
                    )
                }
            val blockingClient = KoniferBlockingClient(KoniferClient(httpClient))

            val response =
                blockingClient.storeAsset(
                    path = "/users/123",
                    request = request,
                )

            response::class shouldBe KoniferResponse.Success::class
            (response as KoniferResponse.Success<*>).body shouldBe expectedResponse
        }

        test("should update asset") {
            val request =
                StoreAssetRequest(
                    tags = setOf("tag"),
                    labels = mapOf("test" to "test"),
                    alt = "alt",
                )
            val expectedResponse = createMetadataResponse()
            val httpClient =
                httpClient {
                    configureMockUpdateEngineHappy(
                        expectedPath = "/assets/users/123/-/entry/2",
                        request = request,
                        response = expectedResponse,
                    )
                }
            val blockingClient = KoniferBlockingClient(KoniferClient(httpClient))

            val response =
                blockingClient.updateAsset(
                    path = "/users/123",
                    entryId = 2,
                    request = request,
                )

            response::class shouldBe KoniferResponse.Success::class
            (response as KoniferResponse.Success<*>).body shouldBe expectedResponse
        }

        test("should return errors from delegated client calls") {
            val serverResponse = createErrorResponse("not found")
            val httpClient =
                httpClient {
                    configureMockEngineError(
                        expectedPath = "/assets/users/123/-/content",
                        response = serverResponse,
                        statusCode = HttpStatusCode.NotFound,
                    )
                }
            val blockingClient = KoniferBlockingClient(KoniferClient(httpClient))

            val response =
                blockingClient.getAssetContentBytes(
                    path = "/users/123",
                    options = AssetContentRequestOptions.Builder().build(),
                )

            response::class shouldBe KoniferResponse.HttpError::class
            with(response as KoniferResponse.HttpError) {
                httpStatusCode shouldBe HttpStatusCode.NotFound
                message shouldBe serverResponse.message
            }
        }

        test("Closing client closes http client") {
            val engine = MockEngine { respondOk() }
            val client = KoniferBlockingClient(KoniferClient(HttpClient(engine)))

            client.close()

            shouldThrow<CancellationException> {
                client.getAssetLink(
                    path = "/users/123",
                )
            }
        }

        test("Closing client closes http redirect client") {
            val engine = MockEngine { respondOk() }
            val client = KoniferBlockingClient(KoniferClient(HttpClient(engine)))

            client.close()

            shouldThrow<CancellationException> {
                client.getAssetRedirectLocation(
                    path = "/users/123",
                )
            }
        }
    })
