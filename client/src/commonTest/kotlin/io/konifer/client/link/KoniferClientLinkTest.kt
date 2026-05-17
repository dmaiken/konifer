package io.konifer.client.link

import io.konifer.client.KoniferClient
import io.konifer.client.KoniferResponse
import io.konifer.client.harness.allTransformationsDsl
import io.konifer.client.harness.configureMockEngineError
import io.konifer.client.harness.createErrorResponse
import io.konifer.client.harness.httpClient
import io.konifer.client.requestedTransformation
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode

class KoniferClientLinkTest :
    FunSpec({
        test("should be able to fetch asset link") {
            val serverResponse = createLinkResponse()
            val httpClient =
                httpClient {
                    configureMockEngineHappy(
                        expectedPath = "/assets/users/123/-/link",
                        response = serverResponse,
                    )
                }

            val koniferClient = KoniferClient(httpClient)

            val response =
                koniferClient.getAssetLink(
                    path = "/users/123",
                )
            response::class shouldBe KoniferResponse.Success::class
            (response as KoniferResponse.Success<*>).body shouldBe serverResponse
        }

        test("should return the error message on a client error") {
            val serverResponse = createErrorResponse("not found")
            val httpClient =
                httpClient {
                    configureMockEngineError(
                        expectedPath = "/assets/users/123/-/link",
                        response = serverResponse,
                        statusCode = HttpStatusCode.NotFound,
                    )
                }

            val koniferClient = KoniferClient(httpClient)

            val response =
                koniferClient.getAssetLink(
                    path = "/users/123",
                )
            response::class shouldBe KoniferResponse.HttpError::class
            (response as KoniferResponse.HttpError).message shouldBe serverResponse.message
        }

        test("should properly translate requested transformation into query parameters") {
            val serverResponse = createLinkResponse()
            val httpClient =
                httpClient {
                    configureMockEngineHappy(
                        expectedPath = "/assets/users/123/-/link",
                        response = serverResponse,
                        requestedTransformation = allTransformationsDsl,
                    )
                }

            val koniferClient = KoniferClient(httpClient)

            val response =
                koniferClient.getAssetLink(
                    path = "/users/123",
                    requestedTransformation = allTransformationsDsl,
                )
            response::class shouldBe KoniferResponse.Success::class
            (response as KoniferResponse.Success<*>).body shouldBe serverResponse
        }
    })
