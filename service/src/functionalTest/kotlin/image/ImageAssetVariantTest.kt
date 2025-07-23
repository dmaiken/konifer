package image

import asset.model.AssetClass
import asset.model.StoreAssetRequest
import config.testInMemory
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.apache.tika.Tika
import org.junit.jupiter.api.Test
import util.byteArrayToImage
import util.createJsonClient
import util.fetchAsset
import util.matcher.shouldBeApproximately
import util.storeAsset

class ImageAssetVariantTest {
    @Test
    fun `can fetch image variant by height`() =
        testInMemory {
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
                    first().imageAttributes.apply {
                        this.height shouldBe bufferedImage.height
                        this.width shouldBe bufferedImage.width
                        this.width.toDouble() / this.height.toDouble() shouldBe originalScale
                    }
                }
            }

            var count = 0
            repeat(2) {
                fetchAsset(client, height = bufferedImage.height - 10, expectCacheHit = (count == 1)).apply {
                    val variantImage = byteArrayToImage(this)
                    variantImage.height shouldBe bufferedImage.height - 10
                    variantImage.width.toDouble() / variantImage.height.toDouble() shouldBeApproximately originalScale
                }
                count++
            }
        }

    @Test
    fun `can fetch image variant by width`() =
        testInMemory {
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
                    first().imageAttributes.apply {
                        this.height shouldBe bufferedImage.height
                        this.width shouldBe bufferedImage.width
                        this.width.toDouble() / this.height.toDouble() shouldBe originalScale
                    }
                }
            }

            var count = 0
            repeat(2) {
                fetchAsset(client, width = bufferedImage.width - 10, expectCacheHit = (count == 1)).apply {
                    val variantImage = byteArrayToImage(this)
                    variantImage.width shouldBe bufferedImage.width - 10
                    variantImage.width.toDouble() / variantImage.height.toDouble() shouldBeApproximately originalScale
                }
                count++
            }
        }

    @Test
    fun `can fetch image variant by height and width and the aspect ratio is respected`() =
        testInMemory {
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
                    first().imageAttributes.apply {
                        this.height shouldBe bufferedImage.height
                        this.width shouldBe bufferedImage.width
                        this.width.toDouble() / this.height.toDouble() shouldBe originalScale
                    }
                }
            }

            var count = 0
            repeat(2) {
                fetchAsset(
                    client,
                    width = bufferedImage.width - 10,
                    height = bufferedImage.height - 10,
                    expectCacheHit = (count == 1),
                ).apply {
                    val variantImage = byteArrayToImage(this)
                    if (variantImage.width == bufferedImage.width - 10) {
                        variantImage.height shouldNotBe bufferedImage.height - 10
                    }
                    if (variantImage.height == bufferedImage.height - 10) {
                        variantImage.width shouldNotBe bufferedImage.width - 10
                    }
                    variantImage.width.toDouble() / variantImage.height.toDouble() shouldBeApproximately originalScale
                }
                count++
            }
        }

    @Test
    fun `can fetch image variant by content type`() =
        testInMemory {
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
                    first().imageAttributes.apply {
                        this.height shouldBe bufferedImage.height
                        this.width shouldBe bufferedImage.width
                        this.width.toDouble() / this.height.toDouble() shouldBe originalScale
                    }
                }
            }

            var count = 0
            repeat(2) {
                fetchAsset(client, mimeType = "image/jpeg", expectCacheHit = (count == 1)).apply {
                    val variantImage = byteArrayToImage(this)
                    variantImage.width shouldBe bufferedImage.width
                    variantImage.height shouldBe bufferedImage.height
                    Tika().detect(this) shouldBe "image/jpeg"
                }
                count++
            }
        }
}
