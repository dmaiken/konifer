package io.konifer.image

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.VSource
import app.photofox.vipsffm.Vips
import app.photofox.vipsffm.VipsOption
import app.photofox.vipsffm.enums.VipsDirection
import app.photofox.vipsffm.enums.VipsInteresting
import app.photofox.vipsffm.enums.VipsInterpretation
import io.konifer.byteArrayToImage
import io.konifer.config.testInMemory
import io.konifer.domain.asset.AssetClass
import io.konifer.domain.image.ImageFormat
import io.konifer.infrastructure.StoreAssetRequest
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_INTERESTING
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_QUALITY
import io.konifer.infrastructure.vips.transformation.ColorFilter.greyscaleMatrix3x3
import io.konifer.matchers.shouldBeApproximately
import io.konifer.matchers.shouldHaveSamePixelContentAs
import io.konifer.util.createJsonClient
import io.konifer.util.fetchAssetContent
import io.konifer.util.fetchAssetLink
import io.konifer.util.fetchAssetViaRedirect
import io.konifer.util.storeAssetMultipartSource
import io.kotest.matchers.collections.shouldBeSameSizeAs
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.HttpClient
import io.ktor.http.HttpStatusCode
import org.apache.tika.Tika
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import org.junitpioneer.jupiter.cartesian.CartesianTest
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
                    alt = "an image",
                )
            storeAssetMultipartSource(client, image, request).second!!.apply {
                createdAt shouldNotBe null
                alt shouldBe "an image"
                `class` shouldBe AssetClass.IMAGE

                variants.apply {
                    size shouldBe 1
                    first().attributes.apply {
                        this.height shouldBe bufferedImage.height
                        this.width shouldBe bufferedImage.width
                        this.width.toDouble() / this.height.toDouble() shouldBe originalScale
                    }
                }
            }

            var count = 0
            repeat(2) {
                fetchAssetContent(
                    client,
                    height = bufferedImage.height - 10,
                    expectCacheHit = (count == 1),
                ).second!!.apply {
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
                    alt = "an image",
                )
            storeAssetMultipartSource(client, image, request).second!!.apply {
                createdAt shouldNotBe null
                alt shouldBe "an image"
                `class` shouldBe AssetClass.IMAGE

                variants.apply {
                    size shouldBe 1
                    first().attributes.apply {
                        this.height shouldBe bufferedImage.height
                        this.width shouldBe bufferedImage.width
                        this.width.toDouble() / this.height.toDouble() shouldBe originalScale
                    }
                }
            }

            var count = 0
            repeat(2) {
                fetchAssetContent(client, width = bufferedImage.width - 10, expectCacheHit = (count == 1)).second!!.apply {
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
                    alt = "an image",
                )
            storeAssetMultipartSource(client, image, request).second!!.apply {
                createdAt shouldNotBe null
                alt shouldBe "an image"
                `class` shouldBe AssetClass.IMAGE

                variants.apply {
                    size shouldBe 1
                    first().attributes.apply {
                        this.height shouldBe bufferedImage.height
                        this.width shouldBe bufferedImage.width
                    }
                }
            }

            val variantHeight = bufferedImage.height - 100
            val variantWidth = bufferedImage.width - 100
            var count = 0
            repeat(2) {
                fetchAssetContent(
                    client,
                    width = variantWidth,
                    height = variantHeight,
                    expectCacheHit = (count == 1),
                ).let { (_, bytes) ->
                    val variantImage = byteArrayToImage(bytes!!)
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
                    alt = "an image",
                )
            storeAssetMultipartSource(client, image, request).second!!.apply {
                createdAt shouldNotBe null
                alt shouldBe "an image"
                `class` shouldBe AssetClass.IMAGE

                variants.apply {
                    size shouldBe 1
                    first().attributes.apply {
                        this.height shouldBe bufferedImage.height
                        this.width shouldBe bufferedImage.width
                        this.width.toDouble() / this.height.toDouble() shouldBe originalScale
                    }
                }
            }

            var count = 0
            repeat(2) {
                fetchAssetContent(client, format = "jpg", expectCacheHit = (count == 1)).let { (_, bytes) ->
                    val variantImage = byteArrayToImage(bytes!!)
                    variantImage.width shouldBe bufferedImage.width
                    variantImage.height shouldBe bufferedImage.height
                    Tika().detect(bytes) shouldBe "image/jpeg"
                }
                count++
            }
        }

    @Test
    fun `can fetch image with fit mode of fill`() =
        testInMemory {
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
                    first().attributes.apply {
                        this.height shouldBe bufferedImage.height
                        this.width shouldBe bufferedImage.width
                        this.width.toDouble() / this.height.toDouble() shouldBe originalScale
                    }
                }
            }

            var count = 0
            repeat(2) {
                fetchAssetContent(
                    client,
                    height = 200,
                    width = 200,
                    fit = "fill",
                    expectCacheHit = (count == 1),
                ).second!!.apply {
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
                    alt = "an image",
                )
            storeAssetMultipartSource(client, image, request).second!!.apply {
                createdAt shouldNotBe null
                alt shouldBe "an image"
                `class` shouldBe AssetClass.IMAGE

                variants.apply {
                    size shouldBe 1
                    first().attributes.apply {
                        this.height shouldBe bufferedImage.height
                        this.width shouldBe bufferedImage.width
                        this.width.toDouble() / this.height.toDouble() shouldBe originalScale
                    }
                }
            }

            fetchAssetContent(
                client,
                height = bufferedImage.height,
                width = bufferedImage.width,
                expectCacheHit = true,
            ).second!!.apply {
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
                    alt = "an image",
                )
            storeAssetMultipartSource(client, image, request)

            fetchAssetContent(client, rotate = "270", flip = "V", expectCacheHit = false).second!!.apply {
                Tika().detect(this) shouldBe "image/png"
            }
            val result = fetchAssetContent(client, rotate = "270", flip = "V", expectCacheHit = true).second!!
            Vips.run { arena ->
                val expected =
                    VImage
                        .newFromBytes(arena, image)
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
                    alt = "an image",
                )
            storeAssetMultipartSource(client, image, request)

            fetchAssetContent(client, filter = "greyscale", expectCacheHit = false).second!!.apply {
                Tika().detect(this) shouldBe "image/png"
            }
            val result = fetchAssetContent(client, filter = "greyscale", expectCacheHit = true).second!!
            val expectedStream = ByteArrayOutputStream()
            Vips.run { arena ->
                val linear =
                    VImage
                        .newFromBytes(arena, image)
                        .colourspace(VipsInterpretation.INTERPRETATION_scRGB)
                val matrixImage = VImage.matrixloadSource(arena, VSource.newFromBytes(arena, greyscaleMatrix3x3))

                linear
                    .recomb(matrixImage)
                    .colourspace(VipsInterpretation.INTERPRETATION_sRGB)
                    .writeToStream(expectedStream, ".png")

                val actualImage = ImageIO.read(ByteArrayInputStream(result))
                val expectedImage = ImageIO.read(ByteArrayInputStream(expectedStream.toByteArray()))

                actualImage shouldHaveSamePixelContentAs expectedImage
            }
        }

    @Test
    fun `variant can be fetched that is has a crop with gravity applied`() =
        testInMemory {
            val client = createJsonClient(followRedirects = false)
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()

            val request =
                StoreAssetRequest(
                    alt = "an image",
                )
            storeAssetMultipartSource(client, image, request)

            fetchAssetContent(
                client,
                fit = "crop",
                height = 200,
                width = 200,
                gravity = "entropy",
                expectCacheHit = false,
            ).second!!.apply {
                Tika().detect(this) shouldBe "image/png"
            }
            val result =
                fetchAssetContent(
                    client,
                    fit = "crop",
                    height = 200,
                    width = 200,
                    gravity = "entropy",
                    expectCacheHit = true,
                ).second!!
            val expectedStream = ByteArrayOutputStream()
            Vips.run { arena ->
                VImage
                    .newFromBytes(arena, image)
                    .smartcrop(
                        200,
                        200,
                        VipsOption.Enum(OPTION_INTERESTING, VipsInteresting.INTERESTING_ENTROPY),
                    ).writeToStream(expectedStream, ".png")

                val actualImage = ImageIO.read(ByteArrayInputStream(result))
                val expectedImage = ImageIO.read(ByteArrayInputStream(expectedStream.toByteArray()))

                actualImage shouldHaveSamePixelContentAs expectedImage
            }
        }

    @Nested
    inner class BlurVariantTests {
        @Test
        fun `variant can be fetched that is has a blur applied`() =
            testInMemory {
                val client = createJsonClient(followRedirects = false)
                val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()

                val request =
                    StoreAssetRequest(
                        alt = "an image",
                    )
                storeAssetMultipartSource(client, image, request)

                fetchAssetContent(client, blur = 50, expectCacheHit = false).second!!.apply {
                    Tika().detect(this) shouldBe "image/png"
                }
                val result =
                    fetchAssetContent(
                        client,
                        blur = 50,
                        expectCacheHit = true,
                    ).second!!
                val expectedStream = ByteArrayOutputStream()
                Vips.run { arena ->
                    VImage
                        .newFromBytes(arena, image)
                        .gaussblur(50 / 2.0)
                        .writeToStream(expectedStream, ".png")

                    val actualImage = ImageIO.read(ByteArrayInputStream(result))
                    val expectedImage = ImageIO.read(ByteArrayInputStream(expectedStream.toByteArray()))

                    actualImage shouldHaveSamePixelContentAs expectedImage
                }
            }

        @Test
        fun `original variant is fetched if requesting blur of 0`() =
            testInMemory {
                val client = createJsonClient(followRedirects = false)
                val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()

                val request =
                    StoreAssetRequest(
                        alt = "an image",
                    )
                storeAssetMultipartSource(client, image, request)

                val result =
                    fetchAssetContent(
                        client,
                        blur = 0,
                        expectCacheHit = true,
                    ).second!!

                val original =
                    fetchAssetContent(
                        client,
                        expectCacheHit = true,
                    ).second!!
                result shouldBe original
            }

        @Test
        fun `lqips are not regenerated when requesting variant with blur`() =
            testInMemory(
                """
                paths = [
                    {
                        path = "/**"
                        image {
                            lqip = [ "thumbhash", "blurhash" ]
                        }
                    }
                ]
                """.trimIndent(),
            ) {
                val client = createJsonClient(followRedirects = false)
                val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()

                val request =
                    StoreAssetRequest(
                        alt = "an image",
                    )
                storeAssetMultipartSource(client, image, request)
                val result =
                    fetchAssetLink(
                        client,
                        blur = 50,
                        expectCacheHit = false,
                    )!!

                val original =
                    fetchAssetLink(
                        client,
                        expectCacheHit = true,
                    )!!

                result.lqip.blurhash shouldBe original.lqip.blurhash
                result.lqip.thumbhash shouldBe original.lqip.thumbhash
            }
    }

    @Nested
    inner class QualityTests {
        @ParameterizedTest
        @EnumSource(ImageFormat::class, mode = EnumSource.Mode.EXCLUDE, names = ["PNG", "GIF"])
        fun `variant can be fetched that is has quality applied`(variantFormat: ImageFormat) =
            testInMemory {
                val quality = 40
                val client = createJsonClient(followRedirects = false)
                val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()

                val request =
                    StoreAssetRequest(
                        alt = "an image",
                    )
                storeAssetMultipartSource(client, image, request)

                fetchAssetContent(
                    client,
                    format = variantFormat.format,
                    quality = quality,
                    expectCacheHit = false,
                ).second!!.apply {
                    Tika().detect(this) shouldBe variantFormat.mimeType
                }
                val result =
                    fetchAssetContent(
                        client,
                        format = variantFormat.format,
                        quality = quality,
                        expectCacheHit = true,
                    ).second!!
                val higherQualityResult =
                    fetchAssetContent(
                        client,
                        format = variantFormat.format,
                        quality = quality + 10,
                        expectCacheHit = false,
                    ).second!!
                val expectedStream = ByteArrayOutputStream()
                Vips.run { arena ->
                    VImage
                        .newFromBytes(arena, image)
                        .writeToStream(
                            expectedStream,
                            variantFormat.extension,
                            VipsOption.Int(OPTION_QUALITY, quality),
                        )

                    // Cannot use BufferedImage since AVIF is not supported
                    // PHash would not capture quality differences, so lets just compare filesize
                    result shouldBeSameSizeAs expectedStream.toByteArray()
                    higherQualityResult.size shouldBeGreaterThan expectedStream.toByteArray().size
                }
            }

        @CartesianTest
        fun `variant can be fetched that is has highest or lowest quality applied`(
            @CartesianTest.Enum(ImageFormat::class, mode = CartesianTest.Enum.Mode.EXCLUDE, names = ["PNG"]) variantFormat: ImageFormat,
            @CartesianTest.Values(ints = [1, 100]) quality: Int,
        ) = testInMemory {
            val client = createJsonClient(followRedirects = false)
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()

            val request =
                StoreAssetRequest(
                    alt = "an image",
                )
            storeAssetMultipartSource(client, image, request)

            val result =
                fetchAssetContent(
                    client,
                    format = variantFormat.format,
                    quality = quality,
                    expectCacheHit = false,
                ).second!!
            val expectedStream = ByteArrayOutputStream()
            Vips.run { arena ->
                VImage
                    .newFromBytes(arena, image)
                    .writeToStream(
                        expectedStream,
                        variantFormat.extension,
                        VipsOption.Int(OPTION_QUALITY, quality),
                    )

                result shouldBeSameSizeAs expectedStream.toByteArray()
            }
        }

        @Test
        fun `png encoding is not affected by quality parameter`() =
            testInMemory {
                val client = createJsonClient(followRedirects = false)
                val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()

                val request =
                    StoreAssetRequest(
                        alt = "an image",
                    )
                storeAssetMultipartSource(client, image, request)

                val lowerQualityResult =
                    fetchAssetContent(
                        client,
                        format = ImageFormat.PNG.format,
                        quality = 40,
                        expectCacheHit = true,
                    ).second!!
                val higherQualityResult =
                    fetchAssetContent(
                        client,
                        format = ImageFormat.PNG.format,
                        quality = 100,
                        expectCacheHit = true,
                    ).second!!
                val expectedStream = ByteArrayOutputStream()
                Vips.run { arena ->
                    VImage
                        .newFromBytes(arena, image)
                        .writeToStream(expectedStream, ".png")

                    val lowerQualityImage = ImageIO.read(ByteArrayInputStream(lowerQualityResult))
                    val higherQualityImage = ImageIO.read(ByteArrayInputStream(higherQualityResult))
                    val expectedImage = ImageIO.read(ByteArrayInputStream(expectedStream.toByteArray()))

                    lowerQualityImage shouldHaveSamePixelContentAs higherQualityImage
                    higherQualityImage shouldHaveSamePixelContentAs expectedImage
                }
            }
    }

    @Nested
    inner class PadTests {
        @Test
        fun `can fetch variant with padding`() =
            testInMemory {
                val client = createJsonClient(followRedirects = false)
                val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()

                val request =
                    StoreAssetRequest(
                        alt = "an image",
                    )
                storeAssetMultipartSource(client, image, request)

                val pad = 20
                fetchAssetContent(client, pad = pad, background = "#FF0000", expectCacheHit = false)
                val result =
                    fetchAssetContent(
                        client,
                        pad = pad,
                        background = "#FF0000",
                        expectCacheHit = true,
                    ).second!!
                Vips.run { arena ->
                    val source = VImage.newFromBytes(arena, image)
                    val withBorder = VImage.newFromBytes(arena, result)

                    withBorder.width shouldBe source.width + pad * 2
                    withBorder.height shouldBe source.height + pad * 2
                }
            }

        @Test
        fun `fetching variant with 255 alpha background will return variant with no alpha 255 in background`() =
            testInMemory {
                val client = createJsonClient(followRedirects = false)
                val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()

                val request =
                    StoreAssetRequest(
                        alt = "an image",
                    )
                storeAssetMultipartSource(client, image, request)

                val pad = 20
                val resultWithAlphaDefined =
                    fetchAssetContent(client, pad = pad, background = "#FF0000FF", expectCacheHit = false).second!!
                val resultWithoutAlphaDefined =
                    fetchAssetContent(
                        client,
                        pad = pad,
                        background = "#FF0000",
                        expectCacheHit = true,
                    ).second!!
                val withAlphaDefined = ImageIO.read(ByteArrayInputStream(resultWithAlphaDefined))
                val withoutAlphaDefined = ImageIO.read(ByteArrayInputStream(resultWithoutAlphaDefined))

                withAlphaDefined shouldHaveSamePixelContentAs withoutAlphaDefined
            }

        @Test
        fun `fetching variant without 255 alpha background will return variant with alpha 255 in background`() =
            testInMemory {
                val client = createJsonClient(followRedirects = false)
                val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()

                val request =
                    StoreAssetRequest(
                        alt = "an image",
                    )
                storeAssetMultipartSource(client, image, request)

                val pad = 20
                val resultWithAlphaDefined =
                    fetchAssetContent(client, pad = pad, background = "#FF0000", expectCacheHit = false).second!!
                val resultWithoutAlphaDefined =
                    fetchAssetContent(
                        client,
                        pad = pad,
                        background = "#FF0000FF",
                        expectCacheHit = true,
                    ).second!!
                val withAlphaDefined = ImageIO.read(ByteArrayInputStream(resultWithAlphaDefined))
                val withoutAlphaDefined = ImageIO.read(ByteArrayInputStream(resultWithoutAlphaDefined))

                withAlphaDefined shouldHaveSamePixelContentAs withoutAlphaDefined
            }

        @Test
        fun `fetching with no padding will fetch the original variant`() =
            testInMemory {
                val client = createJsonClient(followRedirects = false)
                val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()

                val request =
                    StoreAssetRequest(
                        alt = "an image",
                    )
                storeAssetMultipartSource(client, image, request)

                val result =
                    fetchAssetContent(
                        client,
                        pad = 0,
                        background = "#FF0000",
                        expectCacheHit = true,
                    ).second!!
                val originalVariant = fetchAssetContent(client).second!!
                ImageIO.read(ByteArrayInputStream(result)) shouldHaveSamePixelContentAs
                    ImageIO.read(ByteArrayInputStream(originalVariant))
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

                fetchAssetViaRedirect(
                    client,
                    height = height,
                    expectCacheHit = false,
                    expectedStatusCode = HttpStatusCode.BadRequest,
                )
            }

        @ParameterizedTest
        @ValueSource(ints = [0, -1])
        fun `cannot request an image with invalid width`(width: Int) =
            testInMemory {
                val client = createJsonClient(followRedirects = false)
                storeAsset(client)

                fetchAssetViaRedirect(
                    client,
                    width = width,
                    expectCacheHit = false,
                    expectedStatusCode = HttpStatusCode.BadRequest,
                )
            }

        @Test
        fun `cannot request an image with invalid fit`() =
            testInMemory {
                val client = createJsonClient(followRedirects = false)
                storeAsset(client)

                fetchAssetViaRedirect(
                    client,
                    fit = "bad",
                    expectCacheHit = false,
                    expectedStatusCode = HttpStatusCode.BadRequest,
                )
            }

        @Test
        fun `cannot request an image with invalid rotate`() =
            testInMemory {
                val client = createJsonClient(followRedirects = false)
                storeAsset(client)

                fetchAssetViaRedirect(
                    client,
                    rotate = "bad",
                    expectCacheHit = false,
                    expectedStatusCode = HttpStatusCode.BadRequest,
                )
            }

        @Test
        fun `cannot request an image with invalid flip`() =
            testInMemory {
                val client = createJsonClient(followRedirects = false)
                storeAsset(client)

                fetchAssetViaRedirect(
                    client,
                    flip = "bad",
                    expectCacheHit = false,
                    expectedStatusCode = HttpStatusCode.BadRequest,
                )
            }

        @Test
        fun `cannot request an image with invalid filter`() =
            testInMemory {
                val client = createJsonClient(followRedirects = false)
                storeAsset(client)

                fetchAssetViaRedirect(
                    client,
                    filter = "bad",
                    expectCacheHit = false,
                    expectedStatusCode = HttpStatusCode.BadRequest,
                )
            }

        @Test
        fun `cannot request an image with invalid gravity`() =
            testInMemory {
                val client = createJsonClient(followRedirects = false)
                storeAsset(client)

                fetchAssetViaRedirect(
                    client,
                    filter = "bad",
                    expectCacheHit = false,
                    expectedStatusCode = HttpStatusCode.BadRequest,
                )
            }

        @ParameterizedTest
        @ValueSource(ints = [-1, 151])
        fun `cannot request an image with invalid blur`(blurAmount: Int) =
            testInMemory {
                val client = createJsonClient(followRedirects = false)
                storeAsset(client)

                fetchAssetViaRedirect(
                    client,
                    blur = blurAmount,
                    expectCacheHit = false,
                    expectedStatusCode = HttpStatusCode.BadRequest,
                )
            }

        @Test
        fun `cannot request an image with invalid pad`() =
            testInMemory {
                val client = createJsonClient(followRedirects = false)
                storeAsset(client)

                fetchAssetViaRedirect(
                    client,
                    pad = -1,
                    expectCacheHit = false,
                    expectedStatusCode = HttpStatusCode.BadRequest,
                )
            }

        @Test
        fun `cannot request an image with invalid background`() =
            testInMemory {
                val client = createJsonClient(followRedirects = false)
                storeAsset(client)

                fetchAssetViaRedirect(
                    client,
                    background = "bad",
                    expectCacheHit = false,
                    expectedStatusCode = HttpStatusCode.BadRequest,
                )
            }

        private suspend fun storeAsset(client: HttpClient) {
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()

            val request =
                StoreAssetRequest(
                    alt = "an image",
                )
            storeAssetMultipartSource(client, image, request)
        }
    }
}
