package io.direkt.image

import io.direkt.byteArrayToImage
import io.direkt.config.testInMemory
import io.direkt.domain.asset.AssetClass
import io.direkt.infrastructure.StoreAssetRequest
import io.direkt.matchers.shouldBeApproximately
import io.direkt.util.createJsonClient
import io.direkt.util.fetchAssetViaRedirect
import io.direkt.util.storeAssetMultipartSource
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.apache.tika.Tika
import org.junit.jupiter.api.Named.named
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
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
                    preprocessing {
                        image {
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
                    alt = "an image",
                )
            storeAssetMultipartSource(client, image, request).second!!.apply {
                createdAt shouldNotBe null
                alt shouldBe "an image"
                `class` shouldBe AssetClass.IMAGE

                variants.apply {
                    size shouldBe 1
                    first().storeBucket shouldBe "assets"
                    first().storeKey shouldNotBe null
                    first().attributes.mimeType shouldBe "image/png"
                    first().attributes.width shouldBe 100
                    first().attributes.width.toDouble() / first().attributes.height.toDouble() shouldBeApproximately originalScale
                }
            }

            val fetchedAsset = fetchAssetViaRedirect(client)!!
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
                    preprocessing {
                        image {
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
                    alt = "an image",
                )
            val storedAssetInfo =
                storeAssetMultipartSource(client, image, request).second!!.apply {
                    createdAt shouldNotBe null
                    alt shouldBe "an image"
                    `class` shouldBe AssetClass.IMAGE

                    variants.apply {
                        size shouldBe 1
                        first().storeBucket shouldBe "assets"
                        first().storeKey shouldNotBe null
                        first().attributes.mimeType shouldBe "image/png"
                        first().attributes.height shouldBe 50
                        first().attributes.width.toDouble() / first().attributes.height.toDouble() shouldBeApproximately
                            originalScale
                    }
                }

            val fetchedAsset = fetchAssetViaRedirect(client, entryId = storedAssetInfo.entryId)!!
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
                preprocessing {
                    image {
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
                alt = "an image",
            )
        val storedAssetInfo =
            storeAssetMultipartSource(client, image, request).second!!.apply {
                createdAt shouldNotBe null
                alt shouldBe "an image"
                `class` shouldBe AssetClass.IMAGE

                variants.apply {
                    size shouldBe 1
                    first().storeBucket shouldBe "assets"
                    first().storeKey shouldNotBe null
                    first().attributes.mimeType shouldBe "image/png"
                    first().attributes.height shouldBe bufferedImage.height
                    first().attributes.width shouldBe bufferedImage.width
                }
            }

        val fetchedAsset = fetchAssetViaRedirect(client, entryId = storedAssetInfo.entryId)!!
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
                preprocessing {
                    image {
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
                alt = "an image",
            )
        val storedAssetInfo =
            storeAssetMultipartSource(client, image, request).second!!.apply {
                createdAt shouldNotBe null
                alt shouldBe "an image"
                `class` shouldBe AssetClass.IMAGE

                variants.apply {
                    size shouldBe 1
                    first().storeBucket shouldBe "assets"
                    first().storeKey shouldNotBe null
                    first().attributes.mimeType shouldBe expectedType
                    first().attributes.height shouldBe bufferedImage.height
                    first().attributes.width shouldBe bufferedImage.width
                }
            }

        val fetchedAsset = fetchAssetViaRedirect(client, entryId = storedAssetInfo.entryId)
        Tika().detect(fetchedAsset) shouldBe expectedType
    }

    @Test
    fun `image preprocessing is available per route`() =
        testInMemory(
            """
            path-configuration = [
                {
                    path = "/**"
                    preprocessing {
                        image {
                            image-format = jpeg
                            max-height = 55
                        }
                    }
                }
                {
                    path = "/Users/*/Profile"
                    preprocessing {
                        image {
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
                    alt = "an image",
                )
            val storedAssetInfo =
                storeAssetMultipartSource(client, image, request, path = "users/123/profile").second!!.apply {
                    createdAt shouldNotBe null
                    alt shouldBe "an image"
                    `class` shouldBe AssetClass.IMAGE

                    variants.apply {
                        size shouldBe 1
                        first().storeBucket shouldBe "assets"
                        first().storeKey shouldNotBe null
                        first().attributes.mimeType shouldBe "image/webp"
                        first().attributes.height shouldBe 50
                        first().attributes.width.toDouble() / first().attributes.height.toDouble() shouldBeApproximately
                            originalScale
                    }
                }

            val fetchedAsset =
                fetchAssetViaRedirect(client, path = "users/123/profile", entryId = storedAssetInfo.entryId)
            Tika().detect(fetchedAsset) shouldBe "image/webp"
        }
}
