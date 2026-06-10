package io.konifer.asset.variant

import io.konifer.BaseFunctionalTest
import io.konifer.ImageFactory
import io.konifer.byteArrayToImage
import io.konifer.client.requestedTransformation
import io.konifer.common.http.StoreAssetRequest
import io.konifer.common.image.ImageFormat
import io.konifer.matchers.shouldBeSuccessful
import io.konifer.matchers.shouldHaveHttpError
import io.konifer.testInMemory
import io.konifer.util.fetchAssetLink
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.apache.tika.Tika
import org.junit.jupiter.api.Test

class VariantProfileTest : BaseFunctionalTest() {
    @Test
    fun `bad request returned when fetching asset variant with non-existent variant profile`() =
        testInMemory(
            """
            variant-profiles {
              small {
                w = 10
              }
            }
            """.trimIndent(),
        ) {
            val (image, attributes) = ImageFactory.testImage(format = ImageFormat.PNG)
            konifer()
                .storeAsset(
                    path = "profile",
                    format = attributes.format,
                    request = StoreAssetRequest(),
                    bytes = image,
                ).shouldBeSuccessful()

            konifer().fetchAssetLink(
                path = "profile",
                requestedTransformation =
                    requestedTransformation {
                        profile = "medium"
                    },
            ) shouldHaveHttpError 400
        }

    @Test
    fun `can fetch variant with variant profile`() =
        testInMemory(
            """
            variant-profiles {
              small {
                w = 10
                format = jpg
              }
            }
            """.trimIndent(),
        ) {
            val (image, attributes) = ImageFactory.testImage(format = ImageFormat.PNG)
            val bufferedImage = byteArrayToImage(image)
            konifer()
                .storeAsset(
                    path = "profile",
                    format = attributes.format,
                    request = StoreAssetRequest(),
                    bytes = image,
                ).shouldBeSuccessful()

            val variant =
                konifer()
                    .fetchAssetContentBytes(
                        path = "profile",
                        requestedTransformation =
                            requestedTransformation {
                                profile = "small"
                            },
                    ).shouldBeSuccessful()
                    .body

            val rendered = byteArrayToImage(variant)
            rendered.width shouldBe 10
            rendered.height shouldNotBe bufferedImage.height
            Tika().detect(variant) shouldBe "image/jpeg"
        }

    @Test
    fun `can fetch variant with profile and overloaded image attributes`() =
        testInMemory(
            """
            variant-profiles {
              small {
                w = 10
                format = jpg
              }
            }
            """.trimIndent(),
        ) {
            val (image, attributes) = ImageFactory.testImage(format = ImageFormat.PNG)
            val bufferedImage = byteArrayToImage(image)
            konifer()
                .storeAsset(
                    path = "profile",
                    format = attributes.format,
                    request = StoreAssetRequest(),
                    bytes = image,
                ).shouldBeSuccessful()

            val variant =
                konifer()
                    .fetchAssetContentBytes(
                        path = "profile",
                        requestedTransformation =
                            requestedTransformation {
                                profile = "small"
                                width = 100
                            },
                    ).shouldBeSuccessful()
                    .body

            val rendered = byteArrayToImage(variant)
            rendered.width shouldBe 100
            rendered.height shouldNotBe bufferedImage.height
            Tika().detect(variant) shouldBe "image/jpeg"
        }

    @Test
    fun `no variant profiles are okay`() =
        testInMemory {
            val (image, attributes) = ImageFactory.testImage()
            konifer()
                .storeAsset(
                    path = "profile",
                    format = attributes.format,
                    request = StoreAssetRequest(),
                    bytes = image,
                ).shouldBeSuccessful()

            konifer().fetchAssetLink(
                path = "profile",
                requestedTransformation =
                    requestedTransformation {
                        profile = "medium"
                    },
            ) shouldHaveHttpError 400
        }

    @Test
    fun `can specify every transformation in a variant profile`() =
        testInMemory(
            """
            variant-profiles {
              small {
                w = 100
                h = 200
                format = jpg
                fit = stretch
                gravity = attention
                rotate = 90
                flip = v
                filter = sepia
                blur = 20
                quality = 70
                pad = 10
                pad-c = "#111111"
                strip = "iptc,exif"
                cs = srgb
              }
            } 
            """.trimIndent(),
        ) {
            val (image, attributes) = ImageFactory.testImage()

            konifer()
                .storeAsset(
                    path = "profile",
                    format = attributes.format,
                    request = StoreAssetRequest(),
                    bytes = image,
                ).shouldBeSuccessful()

            konifer()
                .fetchAssetLink(
                    path = "profile",
                    requestedTransformation =
                        requestedTransformation {
                            profile = "small"
                        },
                ).shouldBeSuccessful()
        }
}
