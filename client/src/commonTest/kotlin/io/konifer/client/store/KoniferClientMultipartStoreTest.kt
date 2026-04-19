package io.konifer.client.store

import io.konifer.client.KoniferClient
import io.konifer.client.KoniferResponse
import io.konifer.client.content.readResourceBytes
import io.konifer.client.harness.httpClient
import io.konifer.client.metadata.createMetadataResponse
import io.konifer.common.http.StoreAssetRequest
import io.konifer.common.image.ImageFormat
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.utils.io.ByteReadChannel

class KoniferClientMultipartStoreTest :
    FunSpec({

        test("should be able to upload an asset supplied as a channel") {
            val imageBytes = readResourceBytes("/joshua-tree/joshua-tree.png")
            val request = StoreAssetRequest()
            val expectedResponse = createMetadataResponse()
            val httpClient =
                httpClient {
                    configureMockMultipartEngineHappy(
                        expectedPath = "/assets/users/123",
                        assetBytes = imageBytes,
                        request = request,
                        response = expectedResponse,
                    )
                }
            val koniferClient = KoniferClient(httpClient)

            val actualResponse =
                koniferClient.storeAsset(
                    path = "/users/123",
                    format = ImageFormat.PNG,
                    request = request,
                    channel = ByteReadChannel(imageBytes),
                )
            actualResponse::class shouldBe KoniferResponse.Success::class
            (actualResponse as KoniferResponse.Success<*>).body shouldBe expectedResponse
        }

        test("should be able to upload an asset supplied as a bytearray") {
            val imageBytes = readResourceBytes("/joshua-tree/joshua-tree.png")
            val request = StoreAssetRequest()
            val expectedResponse = createMetadataResponse()
            val httpClient =
                httpClient {
                    configureMockMultipartEngineHappy(
                        expectedPath = "/assets/users/123",
                        assetBytes = imageBytes,
                        request = request,
                        response = expectedResponse,
                    )
                }
            val koniferClient = KoniferClient(httpClient)

            val actualResponse =
                koniferClient.storeAsset(
                    path = "/users/123",
                    format = ImageFormat.PNG,
                    request = request,
                    bytes = imageBytes,
                )
            actualResponse::class shouldBe KoniferResponse.Success::class
            (actualResponse as KoniferResponse.Success<*>).body shouldBe expectedResponse
        }

        test("throws if request does not contains a URL") {
            val imageBytes = readResourceBytes("/joshua-tree/joshua-tree.png")
            val request =
                StoreAssetRequest(
                    url = "https://localhost/image.jpg",
                )
            val expectedResponse = createMetadataResponse()
            val httpClient =
                httpClient {
                    configureMockMultipartEngineHappy(
                        expectedPath = "/assets/users/123",
                        assetBytes = imageBytes,
                        request = request,
                        response = expectedResponse,
                    )
                }
            val koniferClient = KoniferClient(httpClient)

            shouldThrow<IllegalArgumentException> {
                koniferClient.storeAsset(
                    path = "/users/123",
                    format = ImageFormat.PNG,
                    request = request,
                    bytes = imageBytes,
                )
            }.message shouldBe "URL cannot be supplied when asset content is also supplied"
        }
    })
