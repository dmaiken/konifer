package io.konifer.asset.variant.preprocessing

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import app.photofox.vipsffm.VipsOption
import io.konifer.BaseFunctionalTest
import io.konifer.ImageFactory
import io.konifer.PHash
import io.konifer.client.assets.fetch.requestedTransformation
import io.konifer.common.image.Fit
import io.konifer.common.image.ImageFormat
import io.konifer.infrastructure.vips.transformer.HAMMING_DISTANCE_IDENTICAL
import io.konifer.matchers.shouldBeSuccessful
import io.konifer.testInMemory
import io.konifer.util.pixelsToEncoded
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.awaitility.Awaitility.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junitpioneer.jupiter.cartesian.CartesianTest
import java.time.Duration

class ImagePixelPreprocessingTest : BaseFunctionalTest() {
    @Test
    fun `preserves nonopaque alpha when converting an arbitrary pixel layout to PNG`() =
        testInMemory(pixelPreprocessingConfiguration("garb")) {
            val expected = listOf(10, 20, 30, 64, 128, 200, 255, 127, 40, 50, 60, 192, 70, 80, 90, 255)
            val content =
                pixelsToEncoded(
                    pixels = expected.map(Int::toByte).toByteArray(),
                    width = 2,
                    height = 2,
                    channels = "RGBA",
                    format = ImageFormat.PNG,
                )
            val asset = konifer().assets("/pixel-original")
            asset
                .newAsset()
                .fromBytes(content, ImageFormat.PNG)
                .store()
                .shouldBeSuccessful()
            val variant = asset.variant(requestedTransformation { format = ImageFormat.PNG })
            val encoded = variant.fetchContentBytes().shouldBeSuccessful().body
            Vips.run { arena ->
                val image = VImage.newFromBytes(arena, encoded)
                image.width shouldBe 2
                image.height shouldBe 2
                image.getInt("bands") shouldBe 4
                val buffer = image.writeToMemory().asByteBuffer()
                val pixels = ByteArray(buffer.remaining())
                buffer.get(pixels)
                pixels.map { it.toInt() and 0xff } shouldBe expected
            }
        }

    @ParameterizedTest
    @ValueSource(strings = ["rgb", "bgra"])
    fun `can convert a preprocessed pixel original to PNG`(channels: String) =
        testInMemory(pixelPreprocessingConfiguration(channels)) {
            val asset = konifer().assets("/pixel-original")
            val original =
                asset
                    .newAsset()
                    .fromBytes(sourceImage(), ImageFormat.PNG)
                    .store()
                    .shouldBeSuccessful()
                    .body.variants
                    .single()
            original.attributes.format shouldBe "pixels"
            original.attributes.pixels?.channels shouldBe channels

            val variant = asset.variant(requestedTransformation { format = ImageFormat.PNG })
            val attributes =
                variant
                    .fetchLink()
                    .shouldBeSuccessful()
                    .body.attributes
            attributes.width shouldBe 2
            attributes.height shouldBe 2
            attributes.format shouldBe "png"
            attributes.pixels shouldBe null

            assertEncodedPixels(variant.fetchContentBytes().shouldBeSuccessful().body, width = 2, height = 2)
            assertEncodedPixels(variant.fetchContentBytes().shouldBeSuccessful().body, width = 2, height = 2)
            asset
                .fetchInfo()
                .shouldBeSuccessful()
                .body.variants shouldHaveSize 2
        }

    @ParameterizedTest
    @ValueSource(strings = ["rgb", "bgra"])
    fun `can resize a preprocessed pixel original`(channels: String) =
        testInMemory(pixelPreprocessingConfiguration(channels)) {
            val asset = konifer().assets("/pixel-original")
            asset
                .newAsset()
                .fromBytes(sourceImage(), ImageFormat.PNG)
                .store()
                .shouldBeSuccessful()
            val variant =
                asset.variant(
                    requestedTransformation {
                        width = 1
                        height = 1
                        fit = Fit.FILL
                        format = ImageFormat.PIXELS
                        pixelChannels = channels
                    },
                )

            val attributes =
                variant
                    .fetchLink()
                    .shouldBeSuccessful()
                    .body.attributes
            attributes.width shouldBe 1
            attributes.height shouldBe 1
            attributes.pixels?.channels shouldBe channels
            val expected = if (channels == "rgb") listOf(10, 20, 30) else listOf(30, 20, 10, 255)
            variant
                .fetchContentBytes()
                .shouldBeSuccessful()
                .body
                .map { it.toInt() and 0xff } shouldBe expected
        }

    @Test
    fun `can change the channel order of a preprocessed pixel original`() =
        testInMemory(pixelPreprocessingConfiguration("bgra")) {
            val asset = konifer().assets("/pixel-original")
            asset
                .newAsset()
                .fromBytes(sourceImage(), ImageFormat.PNG)
                .store()
                .shouldBeSuccessful()
            val variant =
                asset.variant(
                    requestedTransformation {
                        format = ImageFormat.PIXELS
                        pixelChannels = "ARGB"
                    },
                )

            val attributes =
                variant
                    .fetchLink()
                    .shouldBeSuccessful()
                    .body.attributes
            attributes.width shouldBe 2
            attributes.height shouldBe 2
            attributes.pixels?.channels shouldBe "argb"
            variant
                .fetchContentBytes()
                .shouldBeSuccessful()
                .body
                .map { it.toInt() and 0xff } shouldBe
                List(4) { listOf(255, 10, 20, 30) }.flatten()
        }

    @Test
    fun `can generate a resized eager PNG variant from a preprocessed pixel original`() =
        testInMemory(pixelPreprocessingConfiguration("bgra", eagerVariant = true)) {
            val asset = konifer().assets("/pixel-original")
            asset
                .newAsset()
                .fromBytes(sourceImage(), ImageFormat.PNG)
                .store()
                .shouldBeSuccessful()

            await().atMost(Duration.ofSeconds(5)).untilCallTo {
                runBlocking {
                    asset
                        .fetchInfo()
                        .shouldBeSuccessful()
                        .body.variants.size
                }
            } matches { it == 2 }

            val variants =
                asset
                    .fetchInfo()
                    .shouldBeSuccessful()
                    .body.variants
            variants.single { it.isOriginalVariant }.attributes.format shouldBe "pixels"
            val attributes = variants.single { !it.isOriginalVariant }.attributes
            attributes.width shouldBe 1
            attributes.height shouldBe 1
            attributes.format shouldBe "png"
            attributes.pixels shouldBe null

            val variant = asset.variant(requestedTransformation { profile = "converted" })
            assertEncodedPixels(variant.fetchContentBytes().shouldBeSuccessful().body, width = 1, height = 1)
        }

    @CartesianTest
    fun `can preprocess image to Pixel format`(
        @CartesianTest.Enum(ImageFormat::class, mode = CartesianTest.Enum.Mode.EXCLUDE, names = ["PIXELS"]) format: ImageFormat,
        @CartesianTest.Values(strings = ["rgb", "bgra"]) pixelChannels: String,
    ) = testInMemory(
        """
        paths {
          "/**" {
            transform {
              preprocessing {
                enabled = true
                format = pixels
                pixel-channels = $pixelChannels
              }
            }
          }
        }
        """.trimIndent(),
    ) {
        val (image, attributes) = ImageFactory.testImage(format = format)

        val variants =
            konifer()
                .assets("/")
                .newAsset()
                .fromBytes(image, attributes.format)
                .store()
                .shouldBeSuccessful()
                .body.variants shouldHaveSize 1
        val originalVariantAttributes = variants.single().attributes

        originalVariantAttributes.format shouldBe ImageFormat.PIXELS.name.lowercase()
        originalVariantAttributes.pixels?.channels shouldBe pixelChannels.lowercase()

        val content =
            konifer()
                .assets("/")
                .originalVariant()
                .fetchContentBytes()
                .shouldBeSuccessful()
                .body

        val emptyRequestContent =
            konifer()
                .assets("/")
                .variant(requestedTransformation { })
                .fetchContentBytes()
                .shouldBeSuccessful()
                .body
        emptyRequestContent.contentEquals(content) shouldBe true

        val matchingContent =
            konifer()
                .assets("/")
                .variant(
                    requestedTransformation {
                        this.format = ImageFormat.PIXELS
                        this.pixelChannels = pixelChannels
                    },
                ).fetchContentBytes()
                .shouldBeSuccessful()
                .body
        matchingContent.contentEquals(content) shouldBe true

        val fetchedVariants =
            konifer()
                .assets("/")
                .fetchInfo()
                .shouldBeSuccessful()
                .body.variants
        fetchedVariants shouldHaveSize 1
        fetchedVariants.single().isOriginalVariant shouldBe true
        fetchedVariants.single().transformation shouldBe null

        val reconstructed =
            pixelsToEncoded(
                pixels = content,
                height = originalVariantAttributes.height,
                width = originalVariantAttributes.width,
                channels = pixelChannels.uppercase(),
                format = format,
            )
        PHash.hammingDistance(reconstructed, image) shouldBeLessThanOrEqual
            HAMMING_DISTANCE_IDENTICAL
    }

    private fun sourceImage(): ByteArray =
        pixelsToEncoded(
            pixels = List(4) { listOf(10, 20, 30) }.flatten().map(Int::toByte).toByteArray(),
            width = 2,
            height = 2,
            channels = "RGB",
            format = ImageFormat.PNG,
        )

    private fun assertEncodedPixels(
        content: ByteArray,
        width: Int,
        height: Int,
    ) {
        Vips.run { arena ->
            val image = VImage.newFromBytes(arena, content)
            image.width shouldBe width
            image.height shouldBe height
            val buffer = image.extractBand(0, VipsOption.Int("n", 3)).writeToMemory().asByteBuffer()
            val pixels = ByteArray(buffer.remaining())
            buffer.get(pixels)
            pixels.map { it.toInt() and 0xff } shouldBe List(width * height) { listOf(10, 20, 30) }.flatten()
        }
    }

    private fun pixelPreprocessingConfiguration(
        channels: String,
        eagerVariant: Boolean = false,
    ): String =
        """
        variant-profiles {
          converted {
            w = 1
            h = 1
            fit = fill
            format = png
          }
        }
        paths {
          "/**" {
            transform {
              preprocessing {
                enabled = true
                format = pixels
                pixel-channels = $channels
              }
              ${if (eagerVariant) "eager-variants = [converted]\non-demand-variant { mode = disabled }" else ""}
            }
          }
        }
        """.trimIndent()
}
