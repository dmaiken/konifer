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
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.io.ByteArrayOutputStream

class FetchAssetPixelsTest : BaseFunctionalTest() {
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
