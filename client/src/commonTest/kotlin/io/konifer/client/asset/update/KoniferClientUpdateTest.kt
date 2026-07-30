package io.konifer.client.asset.update

import io.konifer.client.KoniferClient
import io.konifer.client.KoniferResponse
import io.konifer.client.asset.info.createInfoResponse
import io.konifer.client.harness.httpClient
import io.konifer.common.http.StoreAssetRequest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class KoniferClientUpdateTest :
    FunSpec({
        test("should be able to update an asset") {
            val request =
                StoreAssetRequest(
                    tags = setOf("tag"),
                    labels = mapOf("test" to "test"),
                    alt = "alt",
                )
            val expectedResponse = createInfoResponse()
            val httpClient =
                httpClient {
                    configureMockEngineHappy(
                        expectedPath = "/assets/users/123/-/entry/2",
                        request = request,
                        response = expectedResponse,
                    )
                }

            val koniferClient = KoniferClient(httpClient)

            val actualResponse =
                koniferClient.updateAsset(
                    path = "/users/123",
                    request = request,
                    entryId = 2,
                )
            actualResponse::class shouldBe KoniferResponse.Success::class
            (actualResponse as KoniferResponse.Success<*>).body shouldBe expectedResponse
        }
    })
