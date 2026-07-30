package io.konifer.client.rule

import io.konifer.client.KoniferBlockingClient
import io.konifer.client.KoniferClient
import io.konifer.client.KoniferResponse
import io.konifer.client.asset.content.readResourceBytes
import io.konifer.client.harness.httpClient
import io.konifer.common.image.ImageFormat
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.ByteArrayInputStream

class KoniferBlockingClientEvaluateRulesTest :
    FunSpec({

        test("should evaluate rules against content supplied as a URL") {
            val request = createEvaluateRulesRequest(url = "https://localhost/image.jpg")
            val expectedResponse = createEvaluateRulesResponse()
            val httpClient =
                httpClient {
                    configureMockUrlEngineHappy(
                        request = request,
                        response = expectedResponse,
                    )
                }
            val blockingClient = KoniferBlockingClient(KoniferClient(httpClient))

            val response =
                blockingClient.evaluateRules(
                    request = request,
                )

            response::class shouldBe KoniferResponse.Success::class
            (response as KoniferResponse.Success<*>).body shouldBe expectedResponse
        }

        test("should evaluate rules against content supplied as bytes") {
            val imageBytes = readResourceBytes("/joshua-tree/joshua-tree.png")
            val request = createEvaluateRulesRequest()
            val expectedResponse = createEvaluateRulesResponse()
            val httpClient =
                httpClient {
                    configureMockMultipartEngineHappy(
                        request = request,
                        assetBytes = imageBytes,
                        response = expectedResponse,
                    )
                }
            val blockingClient = KoniferBlockingClient(KoniferClient(httpClient))

            val response =
                blockingClient.evaluateRules(
                    request = request,
                    format = ImageFormat.PNG,
                    bytes = imageBytes,
                )

            response::class shouldBe KoniferResponse.Success::class
            (response as KoniferResponse.Success<*>).body shouldBe expectedResponse
        }

        test("should evaluate rules against content supplied as an input stream") {
            val imageBytes = readResourceBytes("/joshua-tree/joshua-tree.png")
            val request = createEvaluateRulesRequest()
            val expectedResponse = createEvaluateRulesResponse()
            val httpClient =
                httpClient {
                    configureMockMultipartEngineHappy(
                        request = request,
                        assetBytes = imageBytes,
                        response = expectedResponse,
                    )
                }
            val blockingClient = KoniferBlockingClient(KoniferClient(httpClient))

            val response =
                blockingClient.evaluateRules(
                    request = request,
                    format = ImageFormat.PNG,
                    inputStream = ByteArrayInputStream(imageBytes),
                )

            response::class shouldBe KoniferResponse.Success::class
            (response as KoniferResponse.Success<*>).body shouldBe expectedResponse
        }
    })
