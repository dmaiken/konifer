package io.konifer.asset

import io.konifer.BaseFunctionalTest
import io.konifer.ImageFactory
import io.konifer.client.HmacSigningAlgorithm
import io.konifer.client.requestedTransformation
import io.konifer.common.http.StoreAssetRequest
import io.konifer.matchers.shouldBeSuccessful
import io.konifer.matchers.shouldHaveHttpError
import io.konifer.testInMemory
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class UrlSigningTest : BaseFunctionalTest() {
    @Test
    fun `signature is required when url-signing is enabled`() =
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

            konifer()
                .fetchAssetLink(
                    path = "profile",
                    requestedTransformation =
                        requestedTransformation {
                            height = 100
                            width = 200
                        },
                ).shouldBeSuccessful()
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
