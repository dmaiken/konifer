package io.konifer.asset.fetch

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.VTarget
import app.photofox.vipsffm.Vips
import app.photofox.vipsffm.VipsOption
import app.photofox.vipsffm.enums.VipsBandFormat
import app.photofox.vipsffm.enums.VipsInterpretation
import io.konifer.BaseFunctionalTest
import io.konifer.ImageFactory
import io.konifer.PHash
import io.konifer.TestImageType
import io.konifer.client.assets.fetch.requestedTransformation
import io.konifer.common.http.AttributesResponse
import io.konifer.common.image.Fit
import io.konifer.common.image.ImageFormat
import io.konifer.common.image.TransformableColorSpace
import io.konifer.common.image.pixel.PixelDataType
import io.konifer.common.image.pixel.PixelLayout
import io.konifer.infrastructure.vips.VipsOptionNames
import io.konifer.infrastructure.vips.transformer.HAMMING_DISTANCE_IDENTICAL
import io.konifer.matchers.shouldBeSuccessful
import io.konifer.matchers.shouldHaveHttpError
import io.konifer.testInMemory
import io.konifer.util.pixelsToEncoded
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.io.ByteArrayOutputStream
import java.lang.foreign.ValueLayout

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

        @JvmStatic
        fun grayscaleRequests() =
            listOf(
                arguments("GRAY", null),
                arguments("gray", TransformableColorSpace.ORIGIN),
                arguments("gRaY", TransformableColorSpace.GRAYSCALE),
            )
    }

    @Test
    fun `planar and interleaved requests return exact bytes and reuse separate cached variants`() =
        testInMemory {
            val interleaved = listOf(10, 20, 30, 128, 200, 255, 40, 50, 60, 70, 80, 90)
            val planar = listOf(10, 128, 40, 70, 20, 200, 50, 80, 30, 255, 60, 90)
            val image =
                pixelsToEncoded(
                    interleaved.map(Int::toByte).toByteArray(),
                    width = 2,
                    height = 2,
                    channels = "RGB",
                    format = ImageFormat.PNG,
                )
            val asset = konifer().assets("/pixel-layouts")
            asset
                .newAsset()
                .fromBytes(image, ImageFormat.PNG)
                .store()
                .shouldBeSuccessful()

            repeat(2) {
                PixelLayout.entries.forEach { layout ->
                    val variant =
                        asset.variant(
                            requestedTransformation {
                                format = ImageFormat.PIXELS
                                pixelLayout = layout
                            },
                        )
                    val attributes =
                        variant
                            .fetchLink()
                            .shouldBeSuccessful()
                            .body.attributes
                    attributes.pixels?.layout shouldBe layout
                    val content = variant.fetchContent().shouldBeSuccessful().body
                    content.attributes shouldBe attributes
                    val bytes = content.bytes

                    bytes.map { byte -> byte.toInt() and 0xff } shouldBe
                        if (layout == PixelLayout.PLANAR) planar else interleaved
                }
            }

            asset
                .fetchInfo()
                .shouldBeSuccessful()
                .body.variants shouldHaveSize 3
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
            attributes.pixels?.layout shouldBe PixelLayout.INTERLEAVED
            attributes.pixels?.dataType shouldBe PixelDataType.UINT8

            val content =
                variant
                    .fetchContent()
                    .shouldBeSuccessful()
                    .body.bytes
            content.size shouldBe 2 * 2 * channels.length
            content.map { byte -> byte.toInt() and 0xff } shouldBe expected
        }

        asset
            .fetchInfo()
            .shouldBeSuccessful()
            .body.variants shouldHaveSize 2
    }

    @ParameterizedTest
    @MethodSource("grayscaleRequests")
    fun `can fetch RGB content as one grayscale channel and reuse the cached variant`(
        channels: String,
        requestedColorSpace: TransformableColorSpace?,
    ) = testInMemory {
        val sourcePixels = listOf(255, 0, 0, 0, 255, 0, 0, 0, 255, 0, 0, 0).map(Int::toByte).toByteArray()
        val image = pixelsToEncoded(sourcePixels, width = 2, height = 2, channels = "RGB", format = ImageFormat.PNG)
        val asset = konifer().assets("/grayscale-pixels")
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
                    colorSpace = requestedColorSpace
                },
            )
        val content =
            variant
                .fetchContent()
                .shouldBeSuccessful()
                .body.bytes
        content.size shouldBe 4
        val intensities = content.map { it.toInt() and 0xff }
        intensities[0].toDouble() shouldBe (127.0 plusOrMinus 2.0)
        intensities[1].toDouble() shouldBe (220.0 plusOrMinus 2.0)
        intensities[2].toDouble() shouldBe (76.0 plusOrMinus 2.0)
        intensities[3] shouldBe 0

        repeat(2) {
            assertGrayscaleAttributes(
                variant
                    .fetchLink()
                    .shouldBeSuccessful()
                    .body.attributes,
                width = 2,
                height = 2,
            )
            variant
                .fetchContent()
                .shouldBeSuccessful()
                .body.bytes
                .toList() shouldBe content.toList()
        }
        asset
            .fetchInfo()
            .shouldBeSuccessful()
            .body.variants shouldHaveSize 2
    }

    @Test
    fun `grayscale pixel content composites transparent pixels against white`() =
        testInMemory {
            val pixels = listOf(0, 0, 0, 0, 0, 0, 0, 128, 0, 0, 0, 255, 255, 255, 255, 0).map(Int::toByte).toByteArray()
            val image = pixelsToEncoded(pixels, width = 2, height = 2, channels = "RGBA", format = ImageFormat.PNG)
            val asset = konifer().assets("/transparent-grayscale-pixels")
            asset
                .newAsset()
                .fromBytes(image, ImageFormat.PNG)
                .store()
                .shouldBeSuccessful()
            val variant =
                asset.variant(
                    requestedTransformation {
                        format = ImageFormat.PIXELS
                        pixelChannels = "gray"
                    },
                )

            repeat(2) {
                assertGrayscaleAttributes(
                    variant
                        .fetchLink()
                        .shouldBeSuccessful()
                        .body.attributes,
                    width = 2,
                    height = 2,
                )
                variant
                    .fetchContent()
                    .shouldBeSuccessful()
                    .body.bytes
                    .map { it.toInt() and 0xff } shouldBe listOf(255, 127, 0, 255)
            }
        }

    @ParameterizedTest
    @ValueSource(ints = [8, 16])
    fun `grayscale source preserves intensity when fetched as unsigned byte pixels`(bitDepth: Int) =
        testInMemory {
            val asset = konifer().assets("/grayscale-source")
            asset
                .newAsset()
                .fromBytes(grayscalePng(bitDepth), ImageFormat.PNG)
                .store()
                .shouldBeSuccessful()
            val variant =
                asset.variant(
                    requestedTransformation {
                        format = ImageFormat.PIXELS
                        pixelChannels = "gray"
                    },
                )

            repeat(2) {
                assertGrayscaleAttributes(
                    variant
                        .fetchLink()
                        .shouldBeSuccessful()
                        .body.attributes,
                    width = 2,
                    height = 2,
                )
                variant
                    .fetchContent()
                    .shouldBeSuccessful()
                    .body.bytes
                    .map { it.toInt() and 0xff } shouldBe listOf(0, 64, 128, 255)
            }
        }

    @Test
    fun `grayscale channels normalize an inherited pixel output format`() =
        testInMemory(pixelPreprocessingConfiguration("rgb")) {
            val pixels = listOf(0, 64, 128, 255).flatMap { intensity -> List(3) { intensity.toByte() } }.toByteArray()
            val image = pixelsToEncoded(pixels, width = 2, height = 2, channels = "RGB", format = ImageFormat.PNG)
            val asset = konifer().assets("/inherited-grayscale-pixels")
            val original =
                asset
                    .newAsset()
                    .fromBytes(image, ImageFormat.PNG)
                    .store()
                    .shouldBeSuccessful()
                    .body.variants
                    .single()
            original.attributes.format shouldBe "pixels"
            original.attributes.pixels?.channels shouldBe "rgb"
            val variant = asset.variant(requestedTransformation { pixelChannels = "gray" })

            repeat(2) {
                assertGrayscaleAttributes(
                    variant
                        .fetchLink()
                        .shouldBeSuccessful()
                        .body.attributes,
                    width = 2,
                    height = 2,
                )
                variant
                    .fetchContent()
                    .shouldBeSuccessful()
                    .body.bytes
                    .map { it.toInt() and 0xff } shouldBe listOf(0, 64, 128, 255)
            }
            asset
                .fetchInfo()
                .shouldBeSuccessful()
                .body.variants shouldHaveSize 2
        }

    @Test
    fun `can decode and resize a stored grayscale pixel original`() =
        testInMemory(pixelPreprocessingConfiguration("gray")) {
            val image = pixelsToEncoded(ByteArray(2 * 2 * 3) { 64 }, width = 2, height = 2, channels = "RGB", format = ImageFormat.PNG)
            val asset = konifer().assets("/resized-grayscale-pixels")
            val original =
                asset
                    .newAsset()
                    .fromBytes(image, ImageFormat.PNG)
                    .store()
                    .shouldBeSuccessful()
                    .body.variants
                    .single()
            assertGrayscaleAttributes(original.attributes, width = 2, height = 2)
            val variant =
                asset.variant(
                    requestedTransformation {
                        width = 1
                        height = 1
                        fit = Fit.FILL
                        pixelChannels = "gray"
                    },
                )

            repeat(2) {
                assertGrayscaleAttributes(
                    variant
                        .fetchLink()
                        .shouldBeSuccessful()
                        .body.attributes,
                    width = 1,
                    height = 1,
                )
                variant
                    .fetchContent()
                    .shouldBeSuccessful()
                    .body.bytes
                    .map { it.toInt() and 0xff } shouldBe listOf(64)
            }
            asset
                .fetchInfo()
                .shouldBeSuccessful()
                .body.variants shouldHaveSize 2
        }

    @ParameterizedTest
    @EnumSource(TransformableColorSpace::class, names = ["SRGB", "P3"])
    fun `grayscale pixel channels reject explicitly requested color spaces`(requestedColorSpace: TransformableColorSpace) =
        testInMemory {
            val asset = konifer().assets("/conflicting-grayscale-pixels")
            asset
                .newAsset()
                .fromBytes(grayscalePng(8), ImageFormat.PNG)
                .store()
                .shouldBeSuccessful()
            val variant =
                asset.variant(
                    requestedTransformation {
                        format = ImageFormat.PIXELS
                        pixelChannels = "gray"
                        colorSpace = requestedColorSpace
                    },
                )

            variant.fetchLink() shouldHaveHttpError 400
            variant.fetchContent() shouldHaveHttpError 400
            asset
                .fetchInfo()
                .shouldBeSuccessful()
                .body.variants shouldHaveSize 1
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
                    .fetchContent()
                    .shouldBeSuccessful()
                    .body.bytes

            val rawPixelAttributes =
                variant
                    .fetchLink()
                    .shouldBeSuccessful()
                    .body
                    .attributes
            rawPixelAttributes.pixels?.channels shouldBe channels.lowercase()
            rawPixelAttributes.pixels?.layout shouldBe PixelLayout.INTERLEAVED
            rawPixelAttributes.pixels?.dataType shouldBe PixelDataType.UINT8

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
                    .fetchContent()
                    .shouldBeSuccessful()
                    .body.bytes

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
                    .fetchContent()
                    .shouldBeSuccessful()
                    .body.bytes

            val rawPixelAttributes =
                variant
                    .fetchLink()
                    .shouldBeSuccessful()
                    .body
                    .attributes

            rawPixelAttributes.pixels?.channels shouldBe channel.lowercase()
            rawPixelAttributes.pixels?.layout shouldBe PixelLayout.INTERLEAVED
            rawPixelAttributes.pixels?.dataType shouldBe PixelDataType.UINT8

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

    @Test
    fun `quality is ignored when requesting pixel format`() =
        testInMemory {
            val (image, attributes) = ImageFactory.testImage()

            konifer()
                .assets("/users/123/profile-picture")
                .newAsset()
                .fromBytes(image, attributes.format)
                .store()

            val variantWithoutQualitySpecified =
                konifer()
                    .assets("/users/123/profile-picture")
                    .variant(
                        requestedTransformation {
                            format = ImageFormat.PIXELS
                        },
                    )
            val variantWithQualitySpecified =
                konifer()
                    .assets("/users/123/profile-picture")
                    .variant(
                        requestedTransformation {
                            format = ImageFormat.PIXELS
                            quality = 50
                        },
                    )
            val noQualityVariant =
                variantWithoutQualitySpecified
                    .fetchContent()
                    .shouldBeSuccessful()
                    .body.bytes
            val withQualityVariant =
                variantWithQualitySpecified
                    .fetchContent()
                    .shouldBeSuccessful()
                    .body.bytes
            withQualityVariant.contentEquals(noQualityVariant) shouldBe true

            // Ensure only one non-original variant exists
            konifer()
                .assets("/users/123/profile-picture")
                .fetchInfo()
                .shouldBeSuccessful()
                .body
                .variants
                .filterNot { it.isOriginalVariant } shouldHaveSize 1
        }

    private fun assertGrayscaleAttributes(
        attributes: AttributesResponse,
        width: Int,
        height: Int,
    ) {
        attributes.width shouldBe width
        attributes.height shouldBe height
        attributes.format shouldBe "pixels"
        attributes.colorSpace shouldBe "grayscale"
        attributes.pixels?.channels shouldBe "gray"
        attributes.pixels?.layout shouldBe PixelLayout.INTERLEAVED
        attributes.pixels?.dataType shouldBe PixelDataType.UINT8
    }

    private fun grayscalePng(bitDepth: Int): ByteArray {
        var encoded: ByteArray? = null
        Vips.run { arena ->
            val memory =
                if (bitDepth == 16) {
                    arena.allocateFrom(ValueLayout.JAVA_SHORT, 0, 16384, 32768.toShort(), 65535.toShort())
                } else {
                    arena.allocateFrom(ValueLayout.JAVA_BYTE, 0, 64, 128.toByte(), 255.toByte())
                }
            val source =
                VImage
                    .newFromMemory(
                        arena,
                        memory,
                        2,
                        2,
                        1,
                        (if (bitDepth == 16) VipsBandFormat.FORMAT_USHORT else VipsBandFormat.FORMAT_UCHAR).rawValue,
                    ).copy(
                        VipsOption.Enum(
                            VipsOptionNames.OPTION_INTERPRETATION,
                            if (bitDepth == 16) VipsInterpretation.INTERPRETATION_GREY16 else VipsInterpretation.INTERPRETATION_B_W,
                        ),
                    )
            val target = VTarget.newToMemory(arena)
            source.writeToTarget(target, ImageFormat.PNG.extension)
            encoded = target.blob.bytes
        }
        return checkNotNull(encoded)
    }

    private fun pixelPreprocessingConfiguration(channels: String): String =
        """
        paths {
          "/**" {
            transform {
              preprocessing {
                enabled = true
                format = pixels
                pixel-channels = $channels
              }
            }
          }
        }
        """.trimIndent()
}
