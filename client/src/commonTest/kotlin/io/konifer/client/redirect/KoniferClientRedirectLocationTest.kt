package io.konifer.client.redirect

import io.konifer.client.KoniferClient
import io.konifer.client.KoniferResponse
import io.konifer.client.harness.configureMockEngineError
import io.konifer.client.harness.createErrorResponse
import io.konifer.client.harness.httpClient
import io.konifer.client.requestedTransformation
import io.konifer.common.image.Filter
import io.konifer.common.image.Fit
import io.konifer.common.image.Flip
import io.konifer.common.image.Gravity
import io.konifer.common.image.ImageFormat
import io.konifer.common.image.Rotate
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
                koniferClient.getAssetRedirectLocation(
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
                koniferClient.getAssetRedirectLocation(
                    path = "/users/123",
                )
            response::class shouldBe KoniferResponse.HttpError::class
            (response as KoniferResponse.HttpError).message shouldBe serverResponse.message
        }

        test("should properly translate requested transformation into query parameters") {
            val redirectUrl = "https://redirect.io/image.jpg"
            val requestedTransformation =
                requestedTransformation {
                    height(10)
                    width(5)
                    fit(Fit.FIT)
                    filter(Filter.BLACK_WHITE)
                    flip(Flip.H)
                    blur(100)
                    gravity(Gravity.CENTER)
                    format(ImageFormat.GIF)
                    rotate(Rotate.NINETY)
                    quality(55)
                    pad(25)
                    padColor("#123456")
                    profile("profile")
                }
            val httpClient =
                httpClient {
                    configureMockEngineHappyRedirect(
                        expectedPath = "/assets/users/123/-/redirect",
                        redirectLocation = redirectUrl,
                    )
                }

            val koniferClient = KoniferClient(httpClient)

            val response =
                koniferClient.getAssetRedirectLocation(
                    path = "/users/123",
                    requestedTransformation = requestedTransformation,
                )
            response::class shouldBe KoniferResponse.Success::class
            (response as KoniferResponse.Success<*>).body shouldBe redirectUrl
        }
    })
