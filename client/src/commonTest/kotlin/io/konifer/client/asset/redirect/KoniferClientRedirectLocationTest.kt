package io.konifer.client.asset.redirect

import io.konifer.client.KoniferClient
import io.konifer.client.KoniferResponse
import io.konifer.client.harness.allTransformationsDsl
import io.konifer.client.harness.configureMockEngineError
import io.konifer.client.harness.createErrorResponse
import io.konifer.client.harness.httpClient
import io.konifer.client.harness.signedKoniferClient
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode

class KoniferClientRedirectLocationTest :
    FunSpec({

        test("should be able to fetch asset redirect") {
            val redirectUrl = "https://redirect.io/image.jpg"
            val httpClient =
                httpClient {
                    configureMockEngineHappyRedirect(
                        expectedPath = "/assets/users/123/-/redirect",
                        redirectLocation = redirectUrl,
                    )
                }

            val koniferClient = KoniferClient(httpClient)

            val response =
                koniferClient.fetchAssetRedirectLocation(
                    path = "/users/123",
                )
            response::class shouldBe KoniferResponse.Success::class
            (response as KoniferResponse.Success<*>).body shouldBe redirectUrl
        }

        test("should add signature parameter when fetching asset redirect with a signed client") {
            val redirectUrl = "https://redirect.io/image.jpg"
            val httpClient =
                httpClient {
                    configureMockEngineHappyRedirect(
                        expectedPath = "/assets/users/123/-/redirect",
                        redirectLocation = redirectUrl,
                        expectSignature = true,
                    )
                }

            val koniferClient = signedKoniferClient(httpClient)

            val response =
                koniferClient.fetchAssetRedirectLocation(
                    path = "/users/123",
                )

            response::class shouldBe KoniferResponse.Success::class
            (response as KoniferResponse.Success<*>).body shouldBe redirectUrl
        }

        test("should return the error message on a client error") {
            val serverResponse = createErrorResponse("not found")
            val httpClient =
                httpClient {
                    configureMockEngineError(
                        expectedPath = "/assets/users/123/-/redirect",
                        response = serverResponse,
                        statusCode = HttpStatusCode.NotFound,
                    )
                }

            val koniferClient = KoniferClient(httpClient)

            val response =
                koniferClient.fetchAssetRedirectLocation(
                    path = "/users/123",
                )
            response::class shouldBe KoniferResponse.HttpError::class
            with(response as KoniferResponse.HttpError) {
                message shouldBe serverResponse.message
                httpStatusCode shouldBe HttpStatusCode.NotFound.value
            }
        }

        test("should be able to fetch asset redirect with labels") {
            val redirectUrl = "https://redirect.io/image.jpg"
            val labels =
                mapOf(
                    "Camera" to "iphone",
                    "s" to "true",
                )
            val httpClient =
                httpClient {
                    configureMockEngineHappyRedirect(
                        expectedPath = "/assets/users/123/-/redirect",
                        redirectLocation = redirectUrl,
                        labels = labels,
                    )
                }

            val koniferClient = KoniferClient(httpClient)

            val response =
                koniferClient.fetchAssetRedirectLocation(
                    path = "/users/123",
                    labels = labels,
                )
            response::class shouldBe KoniferResponse.Success::class
            (response as KoniferResponse.Success<*>).body shouldBe redirectUrl
        }

        test("should properly translate requested transformation into query parameters") {
            val redirectUrl = "https://redirect.io/image.jpg"
            val httpClient =
                httpClient {
                    configureMockEngineHappyRedirect(
                        expectedPath = "/assets/users/123/-/redirect",
                        redirectLocation = redirectUrl,
                    )
                }

            val koniferClient = KoniferClient(httpClient)

            val response =
                koniferClient.fetchAssetRedirectLocation(
                    path = "/users/123",
                    requestedTransformation = allTransformationsDsl,
                )
            response::class shouldBe KoniferResponse.Success::class
            (response as KoniferResponse.Success<*>).body shouldBe redirectUrl
        }
    })
