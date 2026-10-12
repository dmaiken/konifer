package io.konifer.domain.transformation

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import app.photofox.vipsffm.VipsOption
import app.photofox.vipsffm.enums.VipsInterpretation
import io.konifer.common.image.ImageFormat
import io.konifer.common.image.TransformableColorSpace
import io.konifer.common.image.pixel.PixelDataType
import io.konifer.common.image.pixel.PixelLayout
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.image.toColorSpace
import io.konifer.domain.transformation.pixel.PixelChannels
import io.konifer.domain.transformation.pixel.PixelTransformation
import io.konifer.domain.variant.attribute.Attributes
import io.konifer.infrastructure.datastore.inmemory.InMemoryAssetRepository
import io.konifer.infrastructure.objectstore.inmemory.InMemoryObjectStore
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_BANDS
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_INTERPRETATION
import io.konifer.infrastructure.vips.decode.DecodedVipsImage
import io.konifer.infrastructure.vips.pipeline.VipsPipelines
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ColorspaceNormalizerTest {
    @ParameterizedTest
    @ValueSource(strings = ["srgb", "p3", "grayscale", "cymk", "adobe_rgb"])
    fun `grayscale pixel output overrides the origin color space`(sourceColorSpace: String) =
        runTest {
            ColorspaceNormalizer.normalizeColorSpace(
                requested = RequestedTransformation(format = ImageFormat.PIXELS, pixelChannels = PixelChannels.grayscale),
                normalizedFormat = ImageFormat.PIXELS,
                originalAttributesDeferred = CompletableDeferred(attributes(sourceColorSpace.toColorSpace())),
            ) shouldBe ColorSpace.Grayscale
        }

    @ParameterizedTest
    @ValueSource(strings = ["srgb", "p3"])
    fun `grayscale pixel channels preserve explicit color space requests for validation`(colorSpace: String) =
        runTest {
            ColorspaceNormalizer.normalizeColorSpace(
                requested =
                    RequestedTransformation(
                        format = ImageFormat.PIXELS,
                        pixelChannels = PixelChannels.grayscale,
                        colorSpace = if (colorSpace == "srgb") TransformableColorSpace.SRGB else TransformableColorSpace.P3,
                    ),
                originalAttributesDeferred = CompletableDeferred(),
                normalizedFormat = ImageFormat.PIXELS,
            ) shouldBe colorSpace.toColorSpace()
        }

    @Test
    fun `grayscale pixel channels do not change colorspace for encoded output`() =
        runTest {
            ColorspaceNormalizer.normalizeColorSpace(
                requested = RequestedTransformation(format = ImageFormat.PIXELS, pixelChannels = PixelChannels.grayscale),
                normalizedFormat = ImageFormat.PNG,
                originalAttributesDeferred = CompletableDeferred(attributes(ColorSpace.SRGB)),
            ) shouldBe ColorSpace.SRGB
        }

    @Test
    fun `normalized grayscale output produces grayscale attributes before encoding`() =
        runTest {
            val colorSpace =
                ColorspaceNormalizer.normalizeColorSpace(
                    requested = RequestedTransformation(format = ImageFormat.PIXELS, pixelChannels = PixelChannels.grayscale),
                    normalizedFormat = ImageFormat.PIXELS,
                    originalAttributesDeferred = CompletableDeferred(attributes(ColorSpace.SRGB)),
                )
            val transformation =
                Transformation(
                    width = 2.toDimension(),
                    height = 1.toDimension(),
                    format = ImageFormat.PIXELS,
                    colorSpace = colorSpace,
                    pixels = PixelTransformation(PixelChannels.grayscale),
                )
            Vips.run { arena ->
                val source =
                    VImage
                        .black(arena, 2, 1, VipsOption.Int(OPTION_BANDS, 3))
                        .copy(VipsOption.Enum(OPTION_INTERPRETATION, VipsInterpretation.INTERPRETATION_sRGB))
                val result =
                    VipsPipelines.variantGenerationPipeline.run(
                        arena,
                        DecodedVipsImage(image = source),
                        transformation,
                    )
                result.successful shouldBe true
                val outputAttributes =
                    Attributes.createAttributes(
                        image = result.processed,
                        sourceFormat = ImageFormat.PNG,
                        destinationFormat = ImageFormat.PIXELS,
                        pixelTransformation = transformation.pixels,
                    )
                outputAttributes.colorSpace shouldBe ColorSpace.Grayscale
                outputAttributes.pixels?.channels shouldBe PixelChannels.grayscale
                outputAttributes.pixels?.dataType shouldBe PixelDataType.UINT8
                outputAttributes.pixels?.layout shouldBe PixelLayout.INTERLEAVED
            }
        }

    @Test
    fun `normalizes inherited pixel output to grayscale`() =
        runTest {
            val normalizer = TransformationNormalizer(InMemoryAssetRepository(InMemoryObjectStore()))
            val normalized =
                normalizer.normalize(
                    requested = RequestedTransformation(pixelChannels = PixelChannels.grayscale),
                    originalVariantAttributes = attributes(ColorSpace.SRGB).copy(format = ImageFormat.PIXELS),
                )

            normalized.format shouldBe ImageFormat.PIXELS
            normalized.colorSpace shouldBe ColorSpace.Grayscale
            normalized.pixels?.channels shouldBe PixelChannels.grayscale
            normalized.isColorSpaceLocked shouldBe false
        }

    @Test
    fun `inherited encoded output retains its origin color space`() =
        runTest {
            ColorspaceNormalizer.normalizeColorSpace(
                requested = RequestedTransformation(pixelChannels = PixelChannels.grayscale),
                normalizedFormat = ImageFormat.PNG,
                originalAttributesDeferred = CompletableDeferred(attributes(ColorSpace.P3)),
            ) shouldBe ColorSpace.P3
        }

    private fun attributes(colorSpace: ColorSpace) =
        Attributes(
            width = 2.toDimension(),
            height = 1.toDimension(),
            format = ImageFormat.PNG,
            colorSpace = colorSpace,
        )
}
