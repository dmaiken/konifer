package io.konifer.asset.limit

import io.konifer.BaseFunctionalTest
import io.konifer.ImageFactory.testImage
import io.konifer.client.requestedTransformation
import io.konifer.common.http.StoreAssetRequest
import io.konifer.matchers.shouldBeSuccessful
import io.konifer.matchers.shouldHaveHttpError
import io.konifer.testInMemory
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class OnDemandVariantLimitTest : BaseFunctionalTest() {
    @Test
    fun `if width limits are exceeded then variant is rejected`() =
        testInMemory(
            """
            paths {
              "/**" {
                transform {
                  limits {
                    max-width = 99
                  }
                }
              }
            }
            """.trimIndent(),
        ) {
            val (image, attributes) = testImage()
            konifer()
                .storeAsset(
                    path = "users/123",
                    format = attributes.format,
                    request = StoreAssetRequest(),
                    bytes = image,
                ).shouldBeSuccessful()

            konifer().fetchAssetContentBytes(
                path = "users/123",
                requestedTransformation =
                    requestedTransformation {
                        width = 100
                    },
            ) shouldHaveHttpError 400

            // Height is indirectly set to < 15
            val error =
                konifer().fetchAssetContentBytes(
                    path = "users/123",
                    requestedTransformation =
                        requestedTransformation {
                            height = 500
                        },
                ) shouldHaveHttpError 400

            error.message shouldBe "Invalid transformation"
        }

    @Test
    fun `if height limits are exceeded then variant is rejected`() =
        testInMemory(
            """
            paths {
              "/**" {
                transform {
                  limits {
                    max-height = 99
                  }
                }
              }
            }
            """.trimIndent(),
        ) {
            val (image, attributes) = testImage()
            konifer()
                .storeAsset(
                    path = "users/123",
                    format = attributes.format,
                    request = StoreAssetRequest(),
                    bytes = image,
                ).shouldBeSuccessful()

            konifer().fetchAssetContentBytes(
                path = "users/123",
                requestedTransformation =
                    requestedTransformation {
                        height = 100
                    },
            ) shouldHaveHttpError 400

            // Height is indirectly set to < 15
            val error =
                konifer().fetchAssetContentBytes(
                    path = "users/123",
                    requestedTransformation =
                        requestedTransformation {
                            width = 500
                        },
                ) shouldHaveHttpError 400

            error.message shouldBe "Invalid transformation"
        }

    @Test
    fun `if pixel limits are exceeded then variant is rejected`() =
        testInMemory(
            """
            paths {
              "/**" {
                transform {
                  limits {
                    max-pixels = 100
                  }
                }
              }
            }
            """.trimIndent(),
        ) {
            val (image, attributes) = testImage()
            konifer()
                .storeAsset(
                    path = "users/123",
                    format = attributes.format,
                    request = StoreAssetRequest(),
                    bytes = image,
                ).shouldBeSuccessful()

            konifer().fetchAssetContentBytes(
                path = "users/123",
                requestedTransformation =
                    requestedTransformation {
                        height = 50
                    },
            ) shouldHaveHttpError 400

            val error =
                konifer().fetchAssetContentBytes(
                    path = "users/123",
                    requestedTransformation =
                        requestedTransformation {
                            width = 50
                        },
                ) shouldHaveHttpError 400

            error.message shouldBe "Invalid transformation"
        }
}
