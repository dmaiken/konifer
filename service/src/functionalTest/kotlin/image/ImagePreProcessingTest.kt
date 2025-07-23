package image

import asset.model.AssetClass
import asset.model.StoreAssetRequest
import config.testInMemory
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.apache.tika.Tika
import org.junit.jupiter.api.Named.named
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import util.byteArrayToImage
import util.createJsonClient
import util.fetchAsset
import util.matcher.shouldBeApproximately
import util.storeAsset
import java.util.stream.Stream

class ImagePreProcessingTest {
    companion object {
        @JvmStatic
        fun scalingNotNeededSource(): Stream<Arguments> =
            Stream.of(
                arguments(named("No height or width supplied", null), null),
                arguments(named("Height and width are too large", 5000), 5000),
            )

        @JvmStatic
        fun imageConversionSource(): Stream<Arguments> =
            Stream.of(
                arguments("jpeg", "image/jpeg"),
                arguments("jpg", "image/jpeg"),
                arguments("png", "image/png"),
                arguments("webp", "image/webp"),
                arguments("avif", "image/avif"),
            )
    }

    @Test
    fun `image width is resized when it is too large`() =
        testInMemory(
            """
            path-configuration = [
                {
                    path = "/**"
                    image {
                        preprocessing {
                            max-width = 100
                        }
                    }
                }
            ]
            """.trimIndent(),
        ) {
            val client = createJsonClient(followRedirects = false)
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val bufferedImage = byteArrayToImage(image)
            val originalScale = bufferedImage.width.toDouble() / bufferedImage.height.toDouble()
            val request =
                StoreAssetRequest(
                    type = "image/png",
                    alt = "an image",
                )
            storeAsset(client, image, request)!!.apply {
                createdAt shouldNotBe null
                alt shouldBe "an image"
                `class` shouldBe AssetClass.IMAGE

                variants.apply {
                    size shouldBe 1
                    first().bucket shouldBe "assets"
                    first().storeKey shouldNotBe null
                    first().imageAttributes.mimeType shouldBe "image/png"
                    first().imageAttributes.width shouldBe 100
                    first().imageAttributes.width.toDouble() / first().imageAttributes.height.toDouble() shouldBeApproximately originalScale
                }
            }

            val fetchedAsset = fetchAsset(client)
            Tika().detect(fetchedAsset) shouldBe "image/png"
            val fetchedImage = byteArrayToImage(fetchedAsset)
            fetchedImage.width shouldBe 100
            fetchedImage.width.toDouble() / fetchedImage.height.toDouble() shouldBeApproximately originalScale
        }

    @Test
    fun `image height is resized when it is too large`() =
        testInMemory(
            """
            path-configuration = [
                {
                    path = "/**"
                    image {
                        preprocessing {
                            max-height = 50
                        }
                    }
                }
            ]
            """.trimIndent(),
        ) {
            val client = createJsonClient(followRedirects = false)
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val bufferedImage = byteArrayToImage(image)
            val originalScale = bufferedImage.width.toDouble() / bufferedImage.height.toDouble()
            val request =
                StoreAssetRequest(
                    type = "image/png",
                    alt = "an image",
                )
            val storedAssetInfo =
                storeAsset(client, image, request)!!.apply {
                    createdAt shouldNotBe null
                    alt shouldBe "an image"
                    `class` shouldBe AssetClass.IMAGE

                    variants.apply {
                        size shouldBe 1
                        first().bucket shouldBe "assets"
                        first().storeKey shouldNotBe null
                        first().imageAttributes.mimeType shouldBe "image/png"
                        first().imageAttributes.height shouldBe 50
                        first().imageAttributes.width.toDouble() / first().imageAttributes.height.toDouble() shouldBeApproximately
                            originalScale
                    }
                }

            val fetchedAsset = fetchAsset(client, entryId = storedAssetInfo.entryId)
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
        path-configuration = [
            {
                path = "/**"
                image {
                    preprocessing {
                        ${maxHeight?.let { "max-height = $it" } ?: ""}
                        ${maxWidth?.let { "max-width = $it" } ?: ""}
                    }
                }
            }
        ]
        """.trimIndent(),
    ) {
        val client = createJsonClient(followRedirects = false)
        val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
        val bufferedImage = byteArrayToImage(image)
        val request =
            StoreAssetRequest(
                type = "image/png",
                alt = "an image",
            )
        val storedAssetInfo =
            storeAsset(client, image, request)!!.apply {
                createdAt shouldNotBe null
                alt shouldBe "an image"
                `class` shouldBe AssetClass.IMAGE

                variants.apply {
                    size shouldBe 1
                    first().bucket shouldBe "assets"
                    first().storeKey shouldNotBe null
                    first().imageAttributes.mimeType shouldBe "image/png"
                    first().imageAttributes.height shouldBe bufferedImage.height
                    first().imageAttributes.width shouldBe bufferedImage.width
                }
            }

        val fetchedAsset = fetchAsset(client, entryId = storedAssetInfo.entryId)
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
        path-configuration = [
            {
                path = "/**"
                image {
                    preprocessing {
                        image-format = $imageFormat
                    }
                }
            }
        ]
        """.trimIndent(),
    ) {
        val client = createJsonClient(followRedirects = false)
        val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
        val bufferedImage = byteArrayToImage(image)
        val request =
            StoreAssetRequest(
                type = "image/png",
                alt = "an image",
            )
        val storedAssetInfo =
            storeAsset(client, image, request)!!.apply {
                createdAt shouldNotBe null
                alt shouldBe "an image"
                `class` shouldBe AssetClass.IMAGE

                variants.apply {
                    size shouldBe 1
                    first().bucket shouldBe "assets"
                    first().storeKey shouldNotBe null
                    first().imageAttributes.mimeType shouldBe expectedType
                    first().imageAttributes.height shouldBe bufferedImage.height
                    first().imageAttributes.width shouldBe bufferedImage.width
                }
            }

        val fetchedAsset = fetchAsset(client, entryId = storedAssetInfo.entryId)
        Tika().detect(fetchedAsset) shouldBe expectedType
    }

    @Test
    fun `image preprocessing is available per route`() =
        testInMemory(
            """
            path-configuration = [
                {
                    path = "/**"
                    image {
                        preprocessing {
                            image-format = jpeg
                            max-height = 55
                        }
                    }
                }
                {
                    path = "/Users/*/Profile"
                    image {
                        preprocessing {
                            image-format = webp
                            max-height = 50
                        }
                    }
                }
            ]
            """.trimIndent(),
        ) {
            val client = createJsonClient(followRedirects = false)
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val bufferedImage = byteArrayToImage(image)
            val originalScale = bufferedImage.width.toDouble() / bufferedImage.height.toDouble()
            val request =
                StoreAssetRequest(
                    type = "image/png",
                    alt = "an image",
                )
            val storedAssetInfo =
                storeAsset(client, image, request, path = "users/123/profile")!!.apply {
                    createdAt shouldNotBe null
                    alt shouldBe "an image"
                    `class` shouldBe AssetClass.IMAGE

                    variants.apply {
                        size shouldBe 1
                        first().bucket shouldBe "assets"
                        first().storeKey shouldNotBe null
                        first().imageAttributes.mimeType shouldBe "image/webp"
                        first().imageAttributes.height shouldBe 50
                        first().imageAttributes.width.toDouble() / first().imageAttributes.height.toDouble() shouldBeApproximately
                            originalScale
                    }
                }

            val fetchedAsset = fetchAsset(client, path = "users/123/profile", entryId = storedAssetInfo.entryId)
            Tika().detect(fetchedAsset) shouldBe "image/webp"
        }
}
