package io.konifer.client.store

import io.konifer.client.KoniferClient
import io.konifer.client.KoniferResponse
import io.konifer.client.content.readResourceBytes
import io.konifer.client.metadata.createMetadataResponse
import io.konifer.common.http.StoreAssetRequest
import io.konifer.common.image.ImageFormat
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.serialization.json.Json

class KoniferClientStoreTest :
    FunSpec({

        test("should be able to upload an asset supplied as a channel") {
            val imageBytes = readResourceBytes("/joshua-tree/joshua-tree.png")
            val request = StoreAssetRequest()
            val expectedResponse = createMetadataResponse()
            val mockEngine =
                configureMockEngineHappy(
                    expectedPath = "/assets/users/123",
                    assetBytes = imageBytes,
                    request = request,
                    response = expectedResponse,
                )
            val httpClient =
                HttpClient(mockEngine) {
                    install(ContentNegotiation) {
                        json(Json)
                    }
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
            val mockEngine =
                configureMockEngineHappy(
                    expectedPath = "/assets/users/123",
                    assetBytes = imageBytes,
                    request = request,
                    response = expectedResponse,
                )
            val httpClient =
                HttpClient(mockEngine) {
                    install(ContentNegotiation) {
                        json(Json)
                    }
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
    })
