package io.konifer.client.rule

import io.konifer.client.KoniferClient
import io.konifer.client.KoniferResponse
import io.konifer.client.asset.content.readResourceBytes
import io.konifer.client.harness.httpClient
import io.konifer.common.image.ImageFormat
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.utils.io.ByteReadChannel

class KoniferClientMultipartEvaluateRulesTest :
    FunSpec({

        test("should be able to evaluate rules against content supplied as a channel") {
            val imageBytes = readResourceBytes("/joshua-tree/joshua-tree.png")
            val request = createEvaluateRulesRequest()
            val expectedResponse = createEvaluateRulesResponse()
            val httpClient =
                httpClient {
                    configureMockMultipartEngineHappy(
                        assetBytes = imageBytes,
                        request = request,
                        response = expectedResponse,
                    )
                }
            val koniferClient = KoniferClient(httpClient)

            val actualResponse =
                koniferClient.evaluateRules(
                    request = request,
                    format = ImageFormat.PNG,
                    channel = ByteReadChannel(imageBytes),
                )

            actualResponse::class shouldBe KoniferResponse.Success::class
            (actualResponse as KoniferResponse.Success<*>).body shouldBe expectedResponse
        }

        test("should be able to evaluate rules against content supplied as a bytearray") {
            val imageBytes = readResourceBytes("/joshua-tree/joshua-tree.png")
            val request = createEvaluateRulesRequest()
            val expectedResponse = createEvaluateRulesResponse()
            val httpClient =
                httpClient {
                    configureMockMultipartEngineHappy(
                        assetBytes = imageBytes,
                        request = request,
                        response = expectedResponse,
                    )
                }
            val koniferClient = KoniferClient(httpClient)

            val actualResponse =
                koniferClient.evaluateRules(
                    format = ImageFormat.PNG,
                    request = request,
                    bytes = imageBytes,
                )

            actualResponse::class shouldBe KoniferResponse.Success::class
            (actualResponse as KoniferResponse.Success<*>).body shouldBe expectedResponse
        }

        test("throws if URL is supplied with content") {
            val imageBytes = readResourceBytes("/joshua-tree/joshua-tree.png")
            val request = createEvaluateRulesRequest(url = "https://localhost/image.jpg")
            val httpClient =
                httpClient {
                    configureMockMultipartEngineHappy(
                        assetBytes = imageBytes,
                        request = request,
                        response = createEvaluateRulesResponse(),
                    )
                }
            val koniferClient = KoniferClient(httpClient)

            shouldThrow<IllegalArgumentException> {
                koniferClient.evaluateRules(
                    format = ImageFormat.PNG,
                    request = request,
                    bytes = imageBytes,
                )
            }.message shouldBe "URL cannot be supplied when content is also supplied"
        }
    })
