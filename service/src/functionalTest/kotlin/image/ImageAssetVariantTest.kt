package image

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import app.photofox.vipsffm.enums.VipsDirection
import app.photofox.vipsffm.enums.VipsInterpretation
import asset.model.AssetClass
import asset.model.StoreAssetRequest
import config.testInMemory
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.HttpClient
import io.ktor.http.HttpStatusCode
import io.matcher.shouldHaveSamePixelContentAs
import org.apache.tika.Tika
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import util.byteArrayToImage
import util.createJsonClient
import util.fetchAssetViaRedirect
import util.matcher.shouldBeApproximately
import util.storeAsset
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

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

    @Test
    fun `variant can be fetched that is rotated and flipped`() =
        testInMemory {
            val client = createJsonClient(followRedirects = false)
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()

            val request =
                StoreAssetRequest(
                    type = "image/png",
                    alt = "an image",
                )
            storeAsset(client, image, request)

            fetchAssetViaRedirect(client, rotate = "270", flip = "V", expectCacheHit = false)!!.apply {
                Tika().detect(this) shouldBe "image/png"
            }
            val result = fetchAssetViaRedirect(client, rotate = "270", flip = "V", expectCacheHit = true)!!
            Vips.run { arena ->
                val expected =
                    VImage.newFromBytes(arena, image)
                        .rotate(90.0)
                        .flip(VipsDirection.DIRECTION_HORIZONTAL)
                val expectedStream = ByteArrayOutputStream()
                expected.writeToStream(expectedStream, ".png")

                val actualImage = ImageIO.read(ByteArrayInputStream(result))
                val expectedImage = ImageIO.read(ByteArrayInputStream(expectedStream.toByteArray()))

                actualImage shouldHaveSamePixelContentAs expectedImage
            }
        }

    @Test
    fun `variant can be fetched that is has a filter applied`() =
        testInMemory {
            val client = createJsonClient(followRedirects = false)
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()

            val request =
                StoreAssetRequest(
                    type = "image/png",
                    alt = "an image",
                )
            storeAsset(client, image, request)

            fetchAssetViaRedirect(client, filter = "greyscale", expectCacheHit = false)!!.apply {
                Tika().detect(this) shouldBe "image/png"
            }
            val result = fetchAssetViaRedirect(client, filter = "greyscale", expectCacheHit = true)!!
            val expectedStream = ByteArrayOutputStream()
            Vips.run { arena ->
                VImage.newFromBytes(arena, image)
                    .colourspace(VipsInterpretation.INTERPRETATION_GREY16)
                    .writeToStream(expectedStream, ".png")

                val actualImage = ImageIO.read(ByteArrayInputStream(result))
                val expectedImage = ImageIO.read(ByteArrayInputStream(expectedStream.toByteArray()))

                actualImage shouldHaveSamePixelContentAs expectedImage
            }
        }

    @Nested
    inner class InvalidVariantRequestTests {
        @ParameterizedTest
        @ValueSource(ints = [0, -1])
        fun `cannot request an image with invalid height`(height: Int) =
            testInMemory {
                val client = createJsonClient(followRedirects = false)
                storeAsset(client)

                fetchAssetViaRedirect(client, height = height, expectCacheHit = false, expectedStatusCode = HttpStatusCode.BadRequest)
            }

        @ParameterizedTest
        @ValueSource(ints = [0, -1])
        fun `cannot request an image with invalid width`(width: Int) =
            testInMemory {
                val client = createJsonClient(followRedirects = false)
                storeAsset(client)

                fetchAssetViaRedirect(client, width = width, expectCacheHit = false, expectedStatusCode = HttpStatusCode.BadRequest)
            }

        @Test
        fun `cannot request an image with invalid fit`() =
            testInMemory {
                val client = createJsonClient(followRedirects = false)
                storeAsset(client)

                fetchAssetViaRedirect(client, fit = "bad", expectCacheHit = false, expectedStatusCode = HttpStatusCode.BadRequest)
            }

        @Test
        fun `cannot request an image with invalid rotate`() =
            testInMemory {
                val client = createJsonClient(followRedirects = false)
                storeAsset(client)

                fetchAssetViaRedirect(client, rotate = "bad", expectCacheHit = false, expectedStatusCode = HttpStatusCode.BadRequest)
            }

        @Test
        fun `cannot request an image with invalid flip`() =
            testInMemory {
                val client = createJsonClient(followRedirects = false)
                storeAsset(client)

                fetchAssetViaRedirect(client, flip = "bad", expectCacheHit = false, expectedStatusCode = HttpStatusCode.BadRequest)
            }

        @Test
        fun `cannot request an image with invalid filter`() =
            testInMemory {
                val client = createJsonClient(followRedirects = false)
                storeAsset(client)

                fetchAssetViaRedirect(client, filter = "bad", expectCacheHit = false, expectedStatusCode = HttpStatusCode.BadRequest)
            }

        private suspend fun storeAsset(client: HttpClient) {
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()

            val request =
                StoreAssetRequest(
                    type = "image/png",
                    alt = "an image",
                )
            storeAsset(client, image, request)
        }
    }
}
