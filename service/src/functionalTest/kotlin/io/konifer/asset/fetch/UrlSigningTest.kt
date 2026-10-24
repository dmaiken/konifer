package io.konifer.asset.fetch

import io.konifer.BaseFunctionalTest
import io.konifer.ImageFactory
import io.konifer.client.HmacSigningAlgorithm
import io.konifer.client.assets.fetch.requestedTransformation
import io.konifer.common.http.StoreAssetRequest
import io.konifer.matchers.shouldBeSuccessful
import io.konifer.matchers.shouldHaveHttpError
import io.konifer.testInMemory
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class UrlSigningTest : BaseFunctionalTest() {
    @Test
    fun `signature on link request is required when url-signing is enabled`() =
        testInMemory(
            """
            url-signing {
                enabled = true
                secret-key = secret
            }
            """.trimIndent(),
        ) {
            val (image, attributes) = ImageFactory.testImage()
            val request =
                StoreAssetRequest(
                    alt = "an image",
                )
            konifer()
                .storeAsset(
                    path = "profile",
                    format = attributes.format,
                    request = request,
                    bytes = image,
                ).shouldBeSuccessful()

            konifer().fetchAssetLink(path = "profile") shouldHaveHttpError 403
        }

    @Test
    fun `signature on info request is required when url-signing is enabled`() =
        testInMemory(
            """
            url-signing {
                enabled = true
                secret-key = secret
            }
            """.trimIndent(),
        ) {
            val (image, attributes) = ImageFactory.testImage()
            val request =
                StoreAssetRequest(
                    alt = "an image",
                )
            konifer()
                .storeAsset(
                    path = "profile",
                    format = attributes.format,
                    request = request,
                    bytes = image,
                ).shouldBeSuccessful()

            konifer().fetchAssetInfo(path = "profile") shouldHaveHttpError 403
        }

    @ParameterizedTest
    @EnumSource(HmacSigningAlgorithm::class)
    fun `can fetch asset with signature when enabled`(algorithm: HmacSigningAlgorithm) =
        testInMemory(
            """
            url-signing {
                enabled = true
                secret-key = secret
                algorithm = ${algorithm.name.lowercase()}
            }
            """.trimIndent(),
        ) {
            configureKoniferHmacSigning(
                hmacKey = "secret",
                hmacSigningAlgorithm = algorithm,
            )
            val (image, attributes) = ImageFactory.testImage()
            val request =
                StoreAssetRequest(
                    alt = "an image",
                )
            konifer()
                .storeAsset(
                    path = "profile",
                    format = attributes.format,
                    request = request,
                    bytes = image,
                ).shouldBeSuccessful()

            val link =
                konifer()
                    .fetchAssetLink(
                        path = "profile",
                        requestedTransformation =
                            requestedTransformation {
                                height = 100
                                width = 200
                            },
                    ).shouldBeSuccessful()
                    .body
            val url = Url(link.url)
            url.parameters["s"] shouldNotBe null
            url.parameters.getAll("s")!!.size shouldBe 1
            val response = client.get(link.url)
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsBytes().isNotEmpty() shouldBe true
            val tampered = URLBuilder(link.url).apply { parameters["width"] = "201" }.build()
            client.get(tampered).status shouldBe HttpStatusCode.Forbidden
        }

    @ParameterizedTest
    @EnumSource(HmacSigningAlgorithm::class)
    fun `cannot fetch asset with invalid signature when enabled`(algorithm: HmacSigningAlgorithm) =
        testInMemory(
            """
            url-signing {
                enabled = true
                secret-key = secret
                algorithm = ${algorithm.name.lowercase()}
            }
            """.trimIndent(),
        ) {
            configureKoniferHmacSigning(
                hmacKey = "secretttt",
                hmacSigningAlgorithm = algorithm,
            )
            val (image, attributes) = ImageFactory.testImage()
            val request =
                StoreAssetRequest(
                    alt = "an image",
                )
            konifer()
                .storeAsset(
                    path = "profile",
                    format = attributes.format,
                    request = request,
                    bytes = image,
                ).shouldBeSuccessful()

            konifer().fetchAssetLink(
                path = "profile",
                requestedTransformation =
                    requestedTransformation {
                        height = 100
                        width = 200
                    },
            ) shouldHaveHttpError 403
        }
}
