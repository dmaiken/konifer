package io.konifer.client.link

import io.konifer.client.KoniferClient
import io.konifer.client.KoniferResponse
import io.konifer.client.configureMockEngineError
import io.konifer.client.createErrorResponse
import io.konifer.client.requestedTransformation
import io.konifer.common.image.Filter
import io.konifer.common.image.Fit
import io.konifer.common.image.Flip
import io.konifer.common.image.Gravity
import io.konifer.common.image.ImageFormat
import io.konifer.common.image.Rotate
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class KoniferClientLinkTest :
    FunSpec({
        test("should be able to fetch asset link") {
            val serverResponse = createLinkResponse()
            val mockEngine =
                configureMockEngineHappy(
                    expectedPath = "/assets/users/123/-/link",
                    response = serverResponse,
                )
            val httpClient =
                HttpClient(mockEngine) {
                    install(ContentNegotiation) {
                        json(Json)
                    }
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
            val mockEngine =
                configureMockEngineError(
                    expectedPath = "/assets/users/123/-/link",
                    response = serverResponse,
                    statusCode = HttpStatusCode.NotFound,
                )
            val httpClient =
                HttpClient(mockEngine) {
                    install(ContentNegotiation) {
                        json(Json)
                    }
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
            val mockEngine =
                configureMockEngineHappy(
                    expectedPath = "/assets/users/123/-/link",
                    response = serverResponse,
                    requestedTransformation = requestedTransformation,
                )
            val httpClient =
                HttpClient(mockEngine) {
                    install(ContentNegotiation) {
                        json(Json)
                    }
                }

            val koniferClient = KoniferClient(httpClient)

            val response =
                koniferClient.getAssetLink(
                    path = "/users/123",
                    requestedTransformation = requestedTransformation,
                )
            response::class shouldBe KoniferResponse.Success::class
            (response as KoniferResponse.Success<*>).body shouldBe serverResponse
        }
    })
