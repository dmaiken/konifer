package io.konifer.client.store

import io.konifer.client.KoniferClient
import io.konifer.client.KoniferResponse
import io.konifer.client.harness.httpClient
import io.konifer.client.metadata.createMetadataResponse
import io.konifer.common.http.StoreAssetRequest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe

class KoniferClientUrlStoreTest :
    FunSpec({

        test("should be able to upload an asset supplied as a URL") {
            val request =
                StoreAssetRequest(
                    url = "https://localhost/image.jpg",
                )
            val expectedResponse = createMetadataResponse()
            val httpClient =
                httpClient {
                    configureMockUrlEngineHappy(
                        expectedPath = "/assets/users/123",
                        request = request,
                        response = expectedResponse,
                    )
                }
            val koniferClient = KoniferClient(httpClient)

            val actualResponse =
                koniferClient.storeAsset(
                    path = "/users/123",
                    request = request,
                )
            actualResponse::class shouldBe KoniferResponse.Success::class
            (actualResponse as KoniferResponse.Success<*>).body shouldBe expectedResponse
        }

        withData(
            nameFn = { "URL supplied in request cannot be: [ $it ]" },
            ts = listOf(null, "", " "),
        ) { url: String? ->
            val request =
                StoreAssetRequest(
                    url = url,
                )
            val httpClient =
                httpClient {
                    configureMockUrlEngineHappy(
                        expectedPath = "/assets/users/123",
                        request = request,
                        response = createMetadataResponse(),
                    )
                }
            val koniferClient = KoniferClient(httpClient)

            shouldThrow<IllegalArgumentException> {
                koniferClient.storeAsset(
                    path = "/users/123",
                    request = request,
                )
            }.message shouldBe "URL is required in request"
        }
    })
