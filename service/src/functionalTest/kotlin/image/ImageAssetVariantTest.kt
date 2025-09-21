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
import util.fetchAssetViaRedirect
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
                fetchAssetViaRedirect(client, height = bufferedImage.height - 10, expectCacheHit = (count == 1))!!.apply {
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
                fetchAssetViaRedirect(client, width = bufferedImage.width - 10, expectCacheHit = (count == 1))!!.apply {
                    val variantImage = byteArrayToImage(this)
                    variantImage.width shouldBe bufferedImage.width - 10
                    variantImage.width.toDouble() / variantImage.height.toDouble() shouldBeApproximately originalScale
                }
                count++
            }
        }

    @Test
    fun `can fetch image variant by height and width with scale fit and the aspect ratio is respected`() =
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

            val variantHeight = bufferedImage.height - 100
            val variantWidth = bufferedImage.width - 100
            var count = 0
            repeat(2) {
                fetchAssetViaRedirect(
                    client,
                    width = variantWidth,
                    height = variantHeight,
                    expectCacheHit = (count == 1),
                )!!.apply {
                    val variantImage = byteArrayToImage(this)
                    if (variantImage.width == variantWidth) {
                        variantImage.height shouldNotBe variantHeight
                    }
                    if (variantImage.height == variantHeight) {
                        variantImage.width shouldNotBe variantWidth
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
                fetchAssetViaRedirect(client, mimeType = "image/jpeg", expectCacheHit = (count == 1))!!.apply {
                    val variantImage = byteArrayToImage(this)
                    variantImage.width shouldBe bufferedImage.width
                    variantImage.height shouldBe bufferedImage.height
                    Tika().detect(this) shouldBe "image/jpeg"
                }
                count++
            }
        }

    @Test
    fun `can fetch image with fit mode of fit`() =
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
                fetchAssetViaRedirect(client, height = 200, width = 200, fit = "fit", expectCacheHit = (count == 1))!!.apply {
                    val variantImage = byteArrayToImage(this)
                    variantImage.width shouldBe 200
                    variantImage.height shouldBe 200
                    Tika().detect(this) shouldBe "image/png"
                }
                count++
            }
        }

    @Test
    fun `can fetch original variant by height and width`() =
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

            fetchAssetViaRedirect(client, height = bufferedImage.height, width = bufferedImage.width, expectCacheHit = true)!!.apply {
                val variantImage = byteArrayToImage(this)
                variantImage.width shouldBe bufferedImage.width
                variantImage.height shouldBe bufferedImage.height
                Tika().detect(this) shouldBe "image/png"
            }
        }
}
