package io.konifer.infrastructure.vips.transformer

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import io.konifer.common.image.ImageFormat
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.image.toColorSpace
import io.konifer.domain.transformation.Transformation
import io.konifer.domain.transformation.toDimension
import io.konifer.infrastructure.vips.ImageColorSpaceExtractor
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource

class TransformColorSpaceTest {
    companion object {
        @JvmStatic
        fun illegalColorSpaceTransformationSource() =
            listOf(
                arguments(ColorSpace.AdobeRGB),
                arguments(ColorSpace.CMYK),
                arguments(ColorSpace.Unknown),
                arguments(ColorSpace.Custom("custom_profile")),
            )

        @JvmStatic
        fun notRequiredTransformationSource() =
            listOf(
                arguments(ColorSpace.SRGB, "/images/metadata/stripped-all.jpg"),
                arguments(ColorSpace.P3, "/images/metadata/iphone-p3.jpg"),
                arguments(ColorSpace.SRGB, "/images/metadata/exif-xmp-iptc.jpg"),
            )
    }

    @Nested
    inner class TransformTests {
        @ParameterizedTest
        @ValueSource(strings = ["srgb", "p3", "grayscale"])
        fun `transforms to supported color space`(colorSpaceName: String) {
            val colorSpace = colorSpaceName.toColorSpace()
            val transformation =
                Transformation(
                    width = 100.toDimension(),
                    height = 100.toDimension(),
                    format = ImageFormat.PNG,
                    colorSpace = colorSpace,
                )

            val image =
                javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.use {
                    it.readBytes()
                }

            Vips.run { arena ->
                val source = VImage.newFromBytes(arena, image)
                val result =
                    TransformColorSpace.transform(
                        arena = arena,
                        source = source,
                        transformation = transformation,
                    )

                result.requiresLqipRegeneration shouldBe true
                ImageColorSpaceExtractor.extract(
                    image = result.processed,
                ) shouldBe colorSpace
            }
        }

        @ParameterizedTest
        @MethodSource("io.konifer.infrastructure.vips.transformer.TransformColorSpaceTest#illegalColorSpaceTransformationSource")
        fun `cannot transform to unsupported color space`(colorSpace: ColorSpace) {
            val transformation =
                Transformation(
                    width = 100.toDimension(),
                    height = 100.toDimension(),
                    format = ImageFormat.PNG,
                    colorSpace = colorSpace,
                )

            val image =
                javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.use {
                    it.readBytes()
                }

            Vips.run { arena ->
                val source = VImage.newFromBytes(arena, image)
                shouldThrow<IllegalArgumentException> {
                    TransformColorSpace.transform(
                        arena = arena,
                        source = source,
                        transformation = transformation,
                    )
                }
            }
        }
    }

    @Nested
    inner class DecisionTests {
        @ParameterizedTest
        @ValueSource(strings = ["srgb", "p3", "grayscale"])
        fun `requires transformation if color space is different from interpreted color space`(colorSpaceName: String) {
            val transformation =
                Transformation(
                    width = 100.toDimension(),
                    height = 100.toDimension(),
                    format = ImageFormat.PNG,
                    colorSpace = colorSpaceName.toColorSpace(),
                )
            val image =
                javaClass.getResourceAsStream("/images/metadata/adobe-rgb.jpg")!!.use {
                    it.readBytes()
                }

            Vips.run { arena ->
                val source = VImage.newFromBytes(arena, image)
                TransformColorSpace.decide(
                    TransformationContext(arena, source, transformation, emptyList()),
                ) shouldBe
                    TransformationDecision.Apply(
                        requiredAlpha = AlphaRequirement.UN_PREMULTIPLIED,
                        requiredPixelAccess = PixelAccess.SEQUENTIAL,
                    )
            }
        }

        @ParameterizedTest
        @MethodSource("io.konifer.infrastructure.vips.transformer.TransformColorSpaceTest#notRequiredTransformationSource")
        fun `if interpretation and transformation are the same then not required`(
            colorSpace: ColorSpace,
            filePath: String,
        ) {
            val transformation =
                Transformation(
                    width = 100.toDimension(),
                    height = 100.toDimension(),
                    format = ImageFormat.PNG,
                    colorSpace = colorSpace,
                )
            val image =
                javaClass.getResourceAsStream(filePath)!!.use {
                    it.readBytes()
                }

            Vips.run { arena ->
                val source = VImage.newFromBytes(arena, image)
                TransformColorSpace.decide(
                    TransformationContext(arena, source, transformation, emptyList()),
                ) shouldBe TransformationDecision.Skip
            }
        }
    }
}
