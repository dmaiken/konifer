package io.konifer.client.rule

import io.konifer.client.KoniferClient
import io.konifer.client.KoniferResponse
import io.konifer.client.harness.httpClient
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe

class KoniferClientUrlEvaluateRulesTest :
    FunSpec({

        test("should be able to evaluate rules against content supplied as a URL") {
            val request = createEvaluateRulesRequest(url = "https://localhost/image.jpg")
            val expectedResponse = createEvaluateRulesResponse()
            val httpClient =
                httpClient {
                    configureMockUrlEngineHappy(
                        request = request,
                        response = expectedResponse,
                    )
                }
            val koniferClient = KoniferClient(httpClient)

            val actualResponse =
                koniferClient.evaluateRules(
                    request = request,
                )

            actualResponse::class shouldBe KoniferResponse.Success::class
            (actualResponse as KoniferResponse.Success<*>).body shouldBe expectedResponse
        }

        withData(
            nameFn = { "URL supplied in request cannot be: [ $it ]" },
            ts = listOf(null, "", " "),
        ) { url: String? ->
            val request = createEvaluateRulesRequest(url = url)
            val httpClient =
                httpClient {
                    configureMockUrlEngineHappy(
                        request = request,
                        response = createEvaluateRulesResponse(),
                    )
                }
            val koniferClient = KoniferClient(httpClient)

            shouldThrow<IllegalArgumentException> {
                koniferClient.evaluateRules(
                    request = request,
                )
            }.message shouldBe "URL is required in request"
        }
    })
