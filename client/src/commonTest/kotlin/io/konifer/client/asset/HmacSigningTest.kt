package io.konifer.client.asset

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.HMAC
import io.konifer.client.HmacSigningAlgorithm
import io.konifer.client.HmacSigningConfig
import io.konifer.client.KoniferClient
import io.konifer.client.KoniferResponse
import io.konifer.client.KoniferUrlSigner
import io.konifer.client.asset.link.createLinkResponse
import io.konifer.client.base64UrlWithoutPadding
import io.konifer.client.harness.httpClient
import io.konifer.client.requestedTransformation
import io.konifer.client.toAlgorithm
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.headersOf
import kotlinx.serialization.json.Json

class HmacSigningTest :
    FunSpec({

        withData(
            nameFn = { "should generate signature with sorted query parameters for algorithm: $it" },
            ts = HmacSigningAlgorithm.entries,
        ) { algorithm ->
            val secret = "secret"
            val signatureGenerator =
                CryptographyProvider.Default
                    .get(HMAC)
                    .keyDecoder(algorithm.toAlgorithm())
                    .decodeFromByteArray(
                        format = HMAC.Key.Format.RAW,
                        bytes = secret.encodeToByteArray(),
                    ).signatureGenerator()
            val urlBuilder =
                URLBuilder().apply {
                    appendPathSegments("assets", "profile", "-", "link")
                    parameters.append("w", "200")
                    parameters.append("h", "100")
                    parameters.append("blur", "50")
                }

            val expectedPayload = "/assets/profile/-/link?blur=50&h=100&w=200".encodeToByteArray()

            val expectedSignature =
                signatureGenerator
                    .generateSignature(expectedPayload)
                    .base64UrlWithoutPadding()

            KoniferUrlSigner
                .create(
                    HmacSigningConfig(
                        secretKey = secret,
                        algorithm = algorithm,
                    ),
                ).sign(urlBuilder) shouldBe expectedSignature
        }

        test("should sign fetch requests when HMAC config is supplied") {
            val serverResponse = createLinkResponse()
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        request.method shouldBe HttpMethod.Get
                        request.url.encodedPath shouldBe "/assets/profile/-/link"
                        request.url.parameters["h"] shouldBe "100"
                        request.url.parameters["w"] shouldBe "200"
                        request.url.parameters["s"] shouldBe "E0Q1wFNo-GyXe2U8qkjyI8u4D180YhUC_xYY3OWn8oc"

                        respond(
                            content = Json.encodeToString(serverResponse),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                }

            val koniferClient =
                KoniferClient(
                    httpClient = httpClient,
                    urlSigner =
                        KoniferUrlSigner.create(
                            HmacSigningConfig(
                                secretKey = "secret",
                            ),
                        ),
                )

            val response =
                koniferClient.fetchAssetLink(
                    path = "profile",
                    requestedTransformation =
                        requestedTransformation {
                            height = 100
                            width = 200
                        },
                )

            response::class shouldBe KoniferResponse.Success::class
        }

        test("should not sign fetch requests when HMAC config is absent") {
            val serverResponse = createLinkResponse()
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        request.method shouldBe HttpMethod.Get
                        request.url.encodedPath shouldBe "/assets/profile/-/link"
                        request.url.parameters["s"] shouldBe null

                        respond(
                            content = Json.encodeToString(serverResponse),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                }

            val koniferClient = KoniferClient(httpClient)

            val response = koniferClient.fetchAssetLink(path = "profile")

            response::class shouldBe KoniferResponse.Success::class
        }
    })
