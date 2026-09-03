package io.konifer.asset.variant

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import io.konifer.BaseFunctionalTest
import io.konifer.byteArrayToImage
import io.konifer.common.asset.AssetClass
import io.konifer.common.http.StoreAssetRequest
import io.konifer.common.image.ImageFormat
import io.konifer.matchers.shouldBeApproximately
import io.konifer.matchers.shouldBeWithinOneOf
import io.konifer.testInMemory
import io.konifer.util.fetchAssetContent
import io.konifer.util.storeAssetMultipartSource
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotStartWith
import org.apache.tika.Tika
import org.junit.jupiter.api.Named
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class ImagePreProcessingTest : BaseFunctionalTest() {
    companion object {
        @JvmStatic
        fun scalingNotNeededSource(): Stream<Arguments> =
            Stream.of(
                Arguments.arguments(Named.named("No height or width supplied", null), null),
                Arguments.arguments(Named.named("Height and width are too large", 3000), 3000),
            )

        @JvmStatic
        fun imageConversionSource(): List<Arguments> =
            ImageFormat.entries.map {
                Arguments.arguments(
                    it.format,
                    it.mimeType,
                )
            }
    }

    @Test
    fun `image width is resized when it is too large`() =
        testInMemory(
            """
            paths {
              "/**" {
                transform {
                  preprocessing {
                    enabled = true
                    clamp-width = 100
                  }
                }
              }
            }
            """.trimIndent(),
        ) {
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val bufferedImage = byteArrayToImage(image)
            val originalScale = bufferedImage.width.toDouble() / bufferedImage.height.toDouble()
            val request =
                StoreAssetRequest(
                    alt = "an image",
                )
            storeAssetMultipartSource(client, image, request).second!!.apply {
                alt shouldBe "an image"
                `class` shouldBe AssetClass.IMAGE

                variants.apply {
                    size shouldBe 1
                    first().storeBucket shouldBe "assets"
                    first().attributes.format shouldBe "png"
                    first().attributes.width shouldBe 100
                    first().attributes.width.toDouble() / first().attributes.height.toDouble() shouldBeApproximately originalScale
                }
            }

            val fetchedAsset = fetchAssetContent(client).second!!
            Tika().detect(fetchedAsset) shouldBe "image/png"
            val fetchedImage = byteArrayToImage(fetchedAsset)
            fetchedImage.width shouldBe 100
            fetchedImage.width.toDouble() / fetchedImage.height.toDouble() shouldBeApproximately originalScale
        }

    @Test
    fun `image height is resized when it is too large`() =
        testInMemory(
            """
            paths {
              "/**" {
                transform {
                  preprocessing {
                    enabled = true
                    clamp-height = 50
                  }
                }
              }
            }
            """.trimIndent(),
        ) {
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val bufferedImage = byteArrayToImage(image)
            val originalScale = bufferedImage.width.toDouble() / bufferedImage.height.toDouble()
            val request =
                StoreAssetRequest(
                    alt = "an image",
                )
            val storedAssetInfo =
                storeAssetMultipartSource(client, image, request).second!!.apply {
                    alt shouldBe "an image"
                    `class` shouldBe AssetClass.IMAGE

                    variants.apply {
                        size shouldBe 1
                        first().storeBucket shouldBe "assets"
                        first().attributes.format shouldBe "png"
                        first().attributes.height shouldBe 50
                        first().attributes.width.toDouble() / first().attributes.height.toDouble() shouldBeApproximately
                            originalScale
                    }
                }

            val fetchedAsset = fetchAssetContent(client, entryId = storedAssetInfo.entryId).second!!
            Tika().detect(fetchedAsset) shouldBe "image/png"
            val fetchedImage = byteArrayToImage(fetchedAsset)
            fetchedImage.height shouldBe 50
            fetchedImage.width.toDouble() / fetchedImage.height.toDouble() shouldBeApproximately originalScale
        }

    @ParameterizedTest
    @MethodSource("scalingNotNeededSource")
    fun `image is not resized when not needed`(
        maxWidth: Int?,
        maxHeight: Int?,
    ) = testInMemory(
        """
        paths {
          "/**" {
            transform {
              preprocessing {
                enabled = true
                ${maxHeight?.let { "clamp-height = $it" } ?: ""}
                ${maxWidth?.let { "clamp-width = $it" } ?: ""}
              }
            }
          }
        }
        """.trimIndent(),
    ) {
        val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
        val bufferedImage = byteArrayToImage(image)
        val request =
            StoreAssetRequest(
                alt = "an image",
            )
        val storedAssetInfo =
            storeAssetMultipartSource(client, image, request).second!!.apply {
                alt shouldBe "an image"
                `class` shouldBe AssetClass.IMAGE

                variants.apply {
                    size shouldBe 1
                    first().storeBucket shouldBe "assets"
                    first().attributes.format shouldBe "png"
                    first().attributes.height shouldBe bufferedImage.height
                    first().attributes.width shouldBe bufferedImage.width
                }
            }

        val fetchedAsset = fetchAssetContent(client, entryId = storedAssetInfo.entryId).second!!
        Tika().detect(fetchedAsset) shouldBe "image/png"
        val fetchedImage = byteArrayToImage(fetchedAsset)
        fetchedImage.width shouldBe bufferedImage.width
        fetchedImage.height shouldBe bufferedImage.height
    }

    @ParameterizedTest
    @MethodSource("imageConversionSource")
    fun `image is converted if necessary`(
        imageFormat: String,
        expectedType: String,
    ) = testInMemory(
        """
        paths {
          "/**" {
            transform {
              preprocessing {
                enabled = true
                format = $imageFormat
              }
            }
          }
        }
        """.trimIndent(),
    ) {
        val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
        val bufferedImage = byteArrayToImage(image)
        val request =
            StoreAssetRequest(
                alt = "an image",
            )
        val storedAssetInfo =
            storeAssetMultipartSource(client, image, request).second!!.apply {
                alt shouldBe "an image"
                `class` shouldBe AssetClass.IMAGE

                variants.apply {
                    size shouldBe 1
                    first().storeBucket shouldBe "assets"
                    first().attributes.format shouldBe imageFormat
                    first().attributes.height shouldBe bufferedImage.height
                    first().attributes.width shouldBe bufferedImage.width
                }
            }

        val fetchedAsset = fetchAssetContent(client, entryId = storedAssetInfo.entryId).second
        Tika().detect(fetchedAsset) shouldBe expectedType
    }

    @Test
    fun `image preprocessing is available per route`() =
        testInMemory(
            """
            paths {
              "/**" {
                transform {
                  preprocessing {
                    enabled = true
                    format = jpg
                    clamp-height = 55
                  }  
                }
              }
              "/Users/*/Profile" {
                transform {
                  preprocessing {
                    format = webp
                    clamp-height = 50
                  }
                }
              }
            }
            """.trimIndent(),
        ) {
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val bufferedImage = byteArrayToImage(image)
            val originalScale = bufferedImage.width.toDouble() / bufferedImage.height.toDouble()
            val request =
                StoreAssetRequest(
                    alt = "an image",
                )
            val storedAssetInfo =
                storeAssetMultipartSource(client, image, request, path = "users/123/profile").second!!.apply {
                    alt shouldBe "an image"
                    `class` shouldBe AssetClass.IMAGE

                    variants.apply {
                        size shouldBe 1
                        first().storeBucket shouldBe "assets"
                        first().attributes.format shouldBe "webp"
                        first().attributes.height shouldBe 50
                        first().attributes.width.toDouble() / first().attributes.height.toDouble() shouldBeApproximately
                            originalScale
                    }
                }

            val fetchedAsset =
                fetchAssetContent(client, path = "users/123/profile", entryId = storedAssetInfo.entryId).second
            Tika().detect(fetchedAsset) shouldBe "image/webp"
        }

    @Test
    fun `image is not preprocessed if preprocessing is disabled`() =
        testInMemory(
            """
            paths {
              "/**" {
                transform {
                  preprocessing {
                    enabled = false
                    format = jpg
                    clamp-height = 55
                  }
                }
              }
            }
            """.trimIndent(),
        ) {
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val bufferedImage = byteArrayToImage(image)
            val request =
                StoreAssetRequest()
            val storedAssetInfo =
                storeAssetMultipartSource(client, image, request, path = "users/123/profile").second!!.apply {
                    `class` shouldBe AssetClass.IMAGE

                    variants.apply {
                        size shouldBe 1
                        first().storeBucket shouldBe "assets"
                        first().attributes.format shouldBe "png"
                        first().attributes.height shouldBe bufferedImage.height
                        first().attributes.width shouldBe bufferedImage.width
                    }
                }

            val fetchedAsset =
                fetchAssetContent(client, path = "users/123/profile", entryId = storedAssetInfo.entryId).second
            Tika().detect(fetchedAsset) shouldBe "image/png"
        }

    @Test
    fun `image is not preprocessed if preprocessing is disabled in parent path and not defined in current`() =
        testInMemory(
            """
            paths {
              "/**" {
                transform {
                  preprocessing {
                    enabled = false
                    format = jpg
                    clamp-height = 55
                  }
                }
              }
              "/Users/*/Profile" {
                transform {
                  preprocessing {
                    format = webp
                    clamp-height = 50
                  }
                }
              }
            }
            """.trimIndent(),
        ) {
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val bufferedImage = byteArrayToImage(image)
            val request =
                StoreAssetRequest()
            val storedAssetInfo =
                storeAssetMultipartSource(client, image, request, path = "users/123/profile").second!!.apply {
                    `class` shouldBe AssetClass.IMAGE

                    variants.apply {
                        size shouldBe 1
                        first().storeBucket shouldBe "assets"
                        first().attributes.format shouldBe "png"
                        first().attributes.height shouldBe bufferedImage.height
                        first().attributes.width shouldBe bufferedImage.width
                    }
                }

            val fetchedAsset =
                fetchAssetContent(client, path = "users/123/profile", entryId = storedAssetInfo.entryId).second
            Tika().detect(fetchedAsset) shouldBe "image/png"
        }

    @Test
    fun `exif thumbnail data is removed if image is preprocessed`() =
        testInMemory(
            """
            paths {
              "/**" {
                transform {
                  preprocessing {
                    enabled = true
                    clamp-height = 50
                  }
                }
              }
            }
            """.trimIndent(),
        ) {
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val request = StoreAssetRequest()
            val storedAssetInfo = storeAssetMultipartSource(client, image, request).second

            val fetchedAsset = fetchAssetContent(client, entryId = storedAssetInfo!!.entryId).second!!
            Vips.run { arena ->
                val image = VImage.newFromBytes(arena, fetchedAsset)
                image.fields.forAll { it shouldNotStartWith "exif-ifd1" }
            }
        }

    @Test
    fun `metadata is removed if configured`() =
        testInMemory(
            """
            paths {
              "/**" {
                transform {
                  preprocessing {
                    enabled = true
                    strip = [ exif, xmp, iptc ]
                  }
                }
              }
            }
            """.trimIndent(),
        ) {
            val image = javaClass.getResourceAsStream("/images/metadata/exif-xmp-iptc.jpg")!!.readBytes()
            val request = StoreAssetRequest()
            val storedAssetInfo = storeAssetMultipartSource(client, image, request).second

            val fetchedAsset = fetchAssetContent(client, entryId = storedAssetInfo!!.entryId).second!!
            Vips.run { arena ->
                val image = VImage.newFromBytes(arena, fetchedAsset)
                // Cannot really test absence of exif-data since encoder will naturally add back some
                image.fields shouldNotContain "jpeg-thumbnail-data"
                image.fields shouldNotContain "xmp-data"
                image.fields shouldNotContain "iptc-data"
            }
        }

    @Test
    fun `color space can be transformed if configured`() =
        testInMemory(
            """
            paths {
              "/**" {
                transform {
                  preprocessing {
                    enabled = true
                    cs = srgb
                  }
                }
              }
            }
            """.trimIndent(),
        ) {
        }

    /**
     * Test the ByteChannel buffers within Konifer
     */
    @Test
    fun `can preprocess large image`() =
        testInMemory(
            """
            paths {
              "/**" {
                transform {
                  preprocessing {
                    enabled = true
                    clamp-width = 3000
                    format = png
                  }
                }
              }
            }
            """.trimIndent(),
        ) {
            val image = javaClass.getResourceAsStream("/images/large/joshua-tree.jpeg")!!.readBytes()
            val request = StoreAssetRequest()
            val storedAssetInfo = storeAssetMultipartSource(client, image, request).second

            val fetchedAsset = fetchAssetContent(client, entryId = storedAssetInfo!!.entryId).second!!

            Tika().detect(fetchedAsset) shouldBe "image/png"
            Vips.run { arena ->
                val image = VImage.newFromBytes(arena, fetchedAsset)
                image.width shouldBeWithinOneOf 3000
            }
        }
}
