package io.konifer.asset.variant

import io.konifer.BaseFunctionalTest
import io.konifer.ImageFactory
import io.konifer.client.requestedTransformation
import io.konifer.common.http.StoreAssetRequest
import io.konifer.common.image.ManipulationParameters
import io.konifer.matchers.shouldBeSuccessful
import io.konifer.matchers.shouldHaveHttpError
import io.konifer.testInMemory
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EmptySource
import org.junit.jupiter.params.provider.ValueSource

class OnDemandVariantModeTest : BaseFunctionalTest() {
    @Test
    fun `with profile-only mode set then only profile can be specified`() =
        testInMemory(
            """
            variant-profiles {
              thumbnail {
                w = 10
              }
            }
            paths {
              "/**" {
                transform {
                  on-demand-variant {
                    mode = profile_only
                  }
                }
              }
            }
            """.trimIndent(),
        ) {
            val (image, attributes) = ImageFactory.testImage()

            konifer
                .storeAsset(
                    path = "users/123",
                    format = attributes.format,
                    request = StoreAssetRequest(),
                    bytes = image,
                ).shouldBeSuccessful()

            konifer
                .fetchAssetContentBytes(
                    path = "users/123",
                    requestedTransformation =
                        requestedTransformation {
                            profile = "thumbnail"
                        },
                ).shouldBeSuccessful()

            val errorResponse =
                konifer.fetchAssetContentBytes(
                    path = "users/123",
                    requestedTransformation =
                        requestedTransformation {
                            width = 10
                        },
                ) shouldHaveHttpError 400

            errorResponse.message shouldBe "Only '${ManipulationParameters.VARIANT_PROFILE}' can be specified"
        }

    @ParameterizedTest
    @ValueSource(strings = ["mode = enabled"])
    @EmptySource
    fun `with enabled mode set then transformations can be specified`(config: String) =
        testInMemory(
            """
            variant-profiles {
              thumbnail {
                w = 10
              }
            }
            paths {
              "/**" {
                transform {
                  on-demand-variant {
                    $config
                  }
                }
              }
            }
            """.trimIndent(),
        ) {
            val (image, attributes) = ImageFactory.testImage()

            konifer
                .storeAsset(
                    path = "users/123",
                    format = attributes.format,
                    request = StoreAssetRequest(),
                    bytes = image,
                ).shouldBeSuccessful()

            konifer
                .fetchAssetContentBytes(
                    path = "users/123",
                    requestedTransformation =
                        requestedTransformation {
                            profile = "thumbnail"
                        },
                ).shouldBeSuccessful()

            konifer
                .fetchAssetContentBytes(
                    path = "users/123",
                    requestedTransformation =
                        requestedTransformation {
                            width = 10
                        },
                ).shouldBeSuccessful()
        }
}
