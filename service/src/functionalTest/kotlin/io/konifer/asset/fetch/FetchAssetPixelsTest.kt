package io.konifer.asset.fetch

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import app.photofox.vipsffm.VipsOption
import io.konifer.BaseFunctionalTest
import io.konifer.ImageFactory
import io.konifer.PHash
import io.konifer.TestImageType
import io.konifer.client.assets.fetch.requestedTransformation
import io.konifer.common.image.ImageFormat
import io.konifer.common.image.TransformableColorSpace
import io.konifer.infrastructure.vips.VipsOptionNames
import io.konifer.infrastructure.vips.transformer.HAMMING_DISTANCE_IDENTICAL
import io.konifer.matchers.shouldBeSuccessful
import io.konifer.matchers.shouldHaveHttpError
import io.konifer.testInMemory
import io.konifer.util.pixelsToEncoded
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.io.ByteArrayOutputStream

class FetchAssetPixelsTest : BaseFunctionalTest() {
    companion object {
        @JvmStatic
        fun pixelLayouts() =
            listOf(
                arguments("RGB", listOf(10, 20, 30, 128, 200, 255, 40, 50, 60, 70, 80, 90)),
                arguments("BGR", listOf(30, 20, 10, 255, 200, 128, 60, 50, 40, 90, 80, 70)),
                arguments("RGBA", listOf(10, 20, 30, 64, 128, 200, 255, 127, 40, 50, 60, 192, 70, 80, 90, 255)),
                arguments("BGRA", listOf(30, 20, 10, 64, 255, 200, 128, 127, 60, 50, 40, 192, 90, 80, 70, 255)),
                arguments("ARGB", listOf(64, 10, 20, 30, 127, 128, 200, 255, 192, 40, 50, 60, 255, 70, 80, 90)),
                arguments("ABGR", listOf(64, 30, 20, 10, 127, 255, 200, 128, 192, 60, 50, 40, 255, 90, 80, 70)),
            )
    }

    @ParameterizedTest
    @MethodSource("pixelLayouts")
    fun `pixel content preserves exact channel values and row order`(
        channels: String,
        expected: List<Int>,
    ) = testInMemory {
        val sourcePixels =
            listOf(10, 20, 30, 64, 128, 200, 255, 127, 40, 50, 60, 192, 70, 80, 90, 255)
                .map(Int::toByte)
                .toByteArray()
        val image = pixelsToEncoded(sourcePixels, width = 2, height = 2, channels = "RGBA", format = ImageFormat.PNG)
        val asset = konifer().assets("/known-pixels")
        asset
            .newAsset()
            .fromBytes(image, ImageFormat.PNG)
            .store()
            .shouldBeSuccessful()
        val variant =
            asset.variant(
                requestedTransformation {
                    format = ImageFormat.PIXELS
                    pixelChannels = channels
                },
            )

        repeat(2) {
            val attributes =
                variant
                    .fetchLink()
                    .shouldBeSuccessful()
                    .body.attributes
            attributes.width shouldBe 2
            attributes.height shouldBe 2
            attributes.format shouldBe "pixels"
            attributes.pixels?.channels shouldBe channels.lowercase()

            val content = variant.fetchContentBytes().shouldBeSuccessful().body
            content.size shouldBe 2 * 2 * channels.length
            content.map { byte -> byte.toInt() and 0xff } shouldBe expected
        }

        asset
            .fetchInfo()
            .shouldBeSuccessful()
            .body.variants shouldHaveSize 2
    }

    @ParameterizedTest
    @ValueSource(strings = ["RGB", "BGR"])
    fun `can fetch image as array of pixels`(channels: String) =
        testInMemory {
            val (image, attributes) = ImageFactory.testImage()

            konifer()
                .assets("/users/123/profile-picture")
                .newAsset()
                .fromBytes(image, attributes.format)
                .store()

            val variant =
                konifer()
                    .assets("/users/123/profile-picture")
                    .variant(
                        requestedTransformation {
                            format = ImageFormat.PIXELS
                            pixelChannels = channels
                        },
                    )
            val rawPixelVariant =
                variant
                    .fetchContentBytes()
                    .shouldBeSuccessful()
                    .body

            val rawPixelAttributes =
                variant
                    .fetchLink()
                    .shouldBeSuccessful()
                    .body
                    .attributes
            rawPixelAttributes.pixels?.channels shouldBe channels.lowercase()

            val reconstructed =
                pixelsToEncoded(
                    pixels = rawPixelVariant,
                    width = rawPixelAttributes.width,
                    height = rawPixelAttributes.height,
                    channels = channels,
                )

            PHash.hammingDistance(reconstructed, image) shouldBeLessThanOrEqual
                HAMMING_DISTANCE_IDENTICAL
        }

    @ParameterizedTest
    @MethodSource("io.konifer.ImageTestSources#supportsPagedSource")
    fun `can fetch multi-paged image as array of pixels`(sourceFormat: ImageFormat) =
        testInMemory {
            val (image, attributes) =
                ImageFactory.testImage(
                    type = TestImageType.KERMIT,
                    format = sourceFormat,
                )
            val firstPageOutput = ByteArrayOutputStream()
            Vips.run { arena ->
                VImage
                    .newFromBytes(arena, image, VipsOption.Int(VipsOptionNames.OPTION_N, 1))
                    .writeToStream(firstPageOutput, sourceFormat.extension)
            }
            val firstPage = firstPageOutput.toByteArray()

            konifer()
                .assets("/users/123/profile-picture")
                .newAsset()
                .fromBytes(image, attributes.format)
                .store()

            val variant =
                konifer()
                    .assets("/users/123/profile-picture")
                    .variant(
                        requestedTransformation {
                            format = ImageFormat.PIXELS
                        },
                    )
            val rawPixelVariant =
                variant
                    .fetchContentBytes()
                    .shouldBeSuccessful()
                    .body

            val rawPixelAttributes =
                variant
                    .fetchLink()
                    .shouldBeSuccessful()
                    .body
                    .attributes

            val reconstructed =
                pixelsToEncoded(
                    pixels = rawPixelVariant,
                    width = rawPixelAttributes.width,
                    height = rawPixelAttributes.height,
                    channels = "RGB",
                )

            PHash.hammingDistance(reconstructed, firstPage) shouldBeLessThanOrEqual
                HAMMING_DISTANCE_IDENTICAL
        }

    @ParameterizedTest
    @ValueSource(strings = ["RGBA", "BGRA"])
    fun `pixel format specifying alpha channel will work if image has no alpha channel`(channel: String) =
        testInMemory {
            val (image, attributes) =
                ImageFactory.testImage(
                    type = TestImageType.JOSHUA_TREE,
                    format = ImageFormat.JPEG, // Does not support alpha
                )

            konifer()
                .assets("/users/123/profile-picture")
                .newAsset()
                .fromBytes(image, attributes.format)
                .store()

            val variant =
                konifer()
                    .assets("/users/123/profile-picture")
                    .variant(
                        requestedTransformation {
                            format = ImageFormat.PIXELS
                            pixelChannels = channel
                        },
                    )
            val rawPixelVariant =
                variant
                    .fetchContentBytes()
                    .shouldBeSuccessful()
                    .body

            val rawPixelAttributes =
                variant
                    .fetchLink()
                    .shouldBeSuccessful()
                    .body
                    .attributes

            rawPixelAttributes.pixels?.channels shouldBe channel.lowercase()

            val reconstructed =
                pixelsToEncoded(
                    pixels = rawPixelVariant,
                    width = rawPixelAttributes.width,
                    height = rawPixelAttributes.height,
                    channels = channel,
                )

            PHash.hammingDistance(reconstructed, image) shouldBeLessThanOrEqual
                HAMMING_DISTANCE_IDENTICAL
        }

    @Test
    fun `grayscale colorspace with rgb pixel format is rejected`() =
        testInMemory {
            val (image, attributes) = ImageFactory.testImage()

            konifer()
                .assets("/users/123/profile-picture")
                .newAsset()
                .fromBytes(image, attributes.format)
                .store()

            konifer()
                .assets("/users/123/profile-picture")
                .variant(
                    requestedTransformation {
                        colorSpace = TransformableColorSpace.GRAYSCALE
                        format = ImageFormat.PIXELS
                    },
                ).fetchLink() shouldHaveHttpError 400
        }

    @Test
    fun `grayscale image stored cannot create variant with rgb pixel format`() =
        testInMemory {
            val (image, attributes) = ImageFactory.testImage(type = TestImageType.GRAY)

            konifer()
                .assets("/users/123/profile-picture")
                .newAsset()
                .fromBytes(image, attributes.format)
                .store()

            konifer()
                .assets("/users/123/profile-picture")
                .variant(
                    requestedTransformation {
                        colorSpace = TransformableColorSpace.ORIGIN
                        format = ImageFormat.PIXELS
                    },
                ).fetchLink() shouldHaveHttpError 400
        }
}
