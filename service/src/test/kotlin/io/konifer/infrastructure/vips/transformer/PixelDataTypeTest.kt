package io.konifer.infrastructure.vips.transformer

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import app.photofox.vipsffm.VipsOption
import app.photofox.vipsffm.enums.VipsBandFormat
import app.photofox.vipsffm.enums.VipsInterpretation
import io.konifer.common.image.ImageFormat
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.transformation.Transformation
import io.konifer.domain.transformation.pixel.PixelTransformation
import io.konifer.domain.transformation.pixel.toPixelChannels
import io.konifer.domain.transformation.toBlur
import io.konifer.domain.transformation.toDimension
import io.konifer.domain.variant.attribute.Attributes
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_INTERPRETATION
import io.konifer.infrastructure.vips.decode.DecodedVipsImage
import io.konifer.infrastructure.vips.encode.RawPixelEncoder
import io.konifer.infrastructure.vips.format
import io.konifer.infrastructure.vips.interpretation
import io.konifer.infrastructure.vips.pipeline.VipsPipelines
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.test.runTest
import kotlinx.io.readByteArray
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import java.lang.foreign.Arena
import java.lang.foreign.ValueLayout
import io.konifer.common.image.pixel.PixelDataType as DataType

class PixelDataTypeTest {
    @Test
    fun `requires unpremultiplied samples before casting raw pixels`() {
        Vips.run { arena ->
            PixelDataType.decide(
                TransformationContext(
                    arena = arena,
                    source = floatImage(arena, "RGB"),
                    transformation("RGB"),
                    appliedTransformations = emptyList(),
                ),
            ) shouldBe
                TransformationDecision.Apply(
                    requiredAlpha = AlphaRequirement.UN_PREMULTIPLIED,
                    requiredPixelAccess = PixelAccess.SEQUENTIAL,
                )
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["RGB", "GRAY"])
    fun `skips already unsigned byte samples`(channels: String) {
        Vips.run { arena ->
            val source = floatImage(arena, channels).cast(VipsBandFormat.FORMAT_UCHAR)
            PixelDataType.decide(
                TransformationContext(
                    arena = arena,
                    source = source,
                    transformation = transformation(channels),
                    appliedTransformations = emptyList(),
                ),
            ) shouldBe TransformationDecision.Skip
        }
    }

    @ParameterizedTest
    @EnumSource(ImageFormat::class, mode = EnumSource.Mode.EXCLUDE, names = ["PIXELS"])
    fun `does not cast samples for encoded formats`(format: ImageFormat) {
        Vips.run { arena ->
            PixelDataType.decide(
                TransformationContext(
                    arena = arena,
                    source = floatImage(arena, "RGB"),
                    transformation = transformation("RGB").copy(format = format, pixels = null),
                    appliedTransformations = emptyList(),
                ),
            ) shouldBe TransformationDecision.Skip
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["RGB", "GRAY"])
    fun `clips and truncates float samples without changing image geometry`(channels: String) {
        Vips.run { arena ->
            val source = floatImage(arena, channels)
            val result =
                PixelDataType.transform(
                    arena = arena,
                    source = source,
                    transformation = transformation(channels),
                )

            result.processed.format() shouldBe VipsBandFormat.FORMAT_UCHAR
            result.processed.width shouldBe source.width
            result.processed.height shouldBe source.height
            result.processed.getInt("bands") shouldBe source.getInt("bands")
            result.requiresLqipRegeneration shouldBe false
            val expected = if (channels == "GRAY") listOf(0, 2, 255, 128) else listOf(0, 2, 255, 255, 128, 0)
            bytes(result.processed).toList() shouldBe expected.map(Int::toByte)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["RGB", "GRAY"])
    fun `pipeline metadata and raw output describe unsigned byte samples`(channels: String) =
        runTest {
            Arena.ofConfined().use { arena ->
                val transformation = transformation(channels)
                val result =
                    VipsPipelines.variantGenerationPipeline.run(
                        arena = arena,
                        source = DecodedVipsImage(image = floatImage(arena, channels)),
                        transformation = transformation,
                    )
                result.successful shouldBe true
                result.processed.format() shouldBe VipsBandFormat.FORMAT_UCHAR
                result.appliedTransformations.map { it.name } shouldBe listOf(PixelDataType.name)
                val attributes =
                    Attributes.createAttributes(
                        image = result.processed,
                        sourceFormat = ImageFormat.PNG,
                        destinationFormat = ImageFormat.PIXELS,
                        pixelTransformation = transformation.pixels,
                    )
                attributes.pixels?.dataType shouldBe DataType.UINT8
                attributes.colorSpace shouldBe transformation.colorSpace
                val expected = if (channels == "GRAY") listOf(0, 2, 255, 128) else listOf(0, 2, 255, 255, 128, 0)
                encode(arena, result.processed, transformation).toList() shouldBe expected.map(Int::toByte)
            }
        }

    @ParameterizedTest
    @ValueSource(strings = ["RGBA", "GRAY"])
    fun `blurred transparent pixels remain unsigned bytes when the pipeline returns`(channels: String) {
        Vips.run { arena ->
            val pixel = if (channels == "GRAY") listOf(160, 128) else listOf(80, 160, 240, 128)
            val samples = List(9 * 9) { pixel }.flatten().map(Int::toByte).toByteArray()
            val source =
                VImage
                    .newFromMemory(
                        arena,
                        arena.allocateFrom(ValueLayout.JAVA_BYTE, *samples),
                        9,
                        9,
                        pixel.size,
                        VipsBandFormat.FORMAT_UCHAR.rawValue,
                    ).copy(
                        VipsOption.Enum(
                            OPTION_INTERPRETATION,
                            if (channels == "GRAY") VipsInterpretation.INTERPRETATION_B_W else VipsInterpretation.INTERPRETATION_sRGB,
                        ),
                    )
            val transformation =
                transformation(channels)
                    .copy(
                        width = 9.toDimension(),
                        height = 9.toDimension(),
                        blur = 2.toBlur(),
                    )
            val result =
                VipsPipelines.variantGenerationPipeline.run(arena, DecodedVipsImage(image = source), transformation)

            result.successful shouldBe true
            result.processed.format() shouldBe VipsBandFormat.FORMAT_UCHAR
            result.processed.hasAlpha() shouldBe true
            bytes(result.processed).size shouldBe samples.size
        }
    }

    @ParameterizedTest
    @EnumSource(VipsBandFormat::class, names = ["FORMAT_USHORT", "FORMAT_FLOAT"])
    fun `preserves grayscale intensity when converting sixteen bit pixels`(format: VipsBandFormat) =
        runTest {
            Arena.ofConfined().use { arena ->
                val source = grayscale16Image(arena).cast(format)
                val transformation = transformation("GRAY")
                val result =
                    VipsPipelines.variantGenerationPipeline.run(
                        arena = arena,
                        source = DecodedVipsImage(image = source),
                        transformation = transformation,
                    )

                result.successful shouldBe true
                result.processed.format() shouldBe VipsBandFormat.FORMAT_UCHAR
                result.processed.interpretation() shouldBe VipsInterpretation.INTERPRETATION_B_W.rawValue
                encode(arena, result.processed, transformation).map { it.toInt() and 0xff } shouldBe listOf(0, 64, 128, 255)
            }
        }

    @ParameterizedTest
    @EnumSource(VipsBandFormat::class, names = ["FORMAT_USHORT", "FORMAT_FLOAT"])
    fun `normalizes sixteen bit alpha before flattening grayscale pixels`(format: VipsBandFormat) =
        runTest {
            Arena.ofConfined().use { arena ->
                val source = grayscale16Image(arena, hasAlpha = true).cast(format)
                val transformation = transformation("GRAY")
                val result =
                    VipsPipelines.variantGenerationPipeline.run(
                        arena = arena,
                        source = DecodedVipsImage(image = source),
                        transformation = transformation,
                    )

                result.successful shouldBe true
                result.processed.format() shouldBe VipsBandFormat.FORMAT_UCHAR
                result.processed.hasAlpha() shouldBe true
                val halfTransparent = result.processed.getpoint(1, 0)
                halfTransparent[0] shouldBe 0.0
                // Half opacity lies between the two nearest 8-bit alpha values.
                halfTransparent[1] shouldBe (127.5 plusOrMinus 0.5)
                result.processed.getpoint(1, 1) shouldBe listOf(255.0, 255.0)
                val output = encode(arena, result.processed, transformation).map { it.toInt() and 0xff }
                output.size shouldBe 4
                output[0] shouldBe 255
                output[1].toDouble() shouldBe (127.5 plusOrMinus 0.5)
                output[2] shouldBe 128
                output[3] shouldBe 255
            }
        }

    @ParameterizedTest
    @EnumSource(ImageFormat::class, names = ["PNG", "JPEG_XL"])
    fun `preserves sixteen bit grayscale precision for encoded output`(format: ImageFormat) {
        Vips.run { arena ->
            val source = grayscale16Image(arena)
            val transformation = transformation("GRAY").copy(format = format, pixels = null)
            val result =
                VipsPipelines.variantGenerationPipeline.run(
                    arena = arena,
                    source = DecodedVipsImage(image = source),
                    transformation = transformation,
                )

            result.successful shouldBe true
            result.processed.format() shouldBe VipsBandFormat.FORMAT_USHORT
            result.processed.interpretation() shouldBe VipsInterpretation.INTERPRETATION_GREY16.rawValue
            result.appliedTransformations shouldBe emptyList()
            bytes(result.processed).toList() shouldBe bytes(source).toList()
        }
    }

    private fun grayscale16Image(
        arena: Arena,
        hasAlpha: Boolean = false,
    ): VImage {
        val samples =
            if (hasAlpha) {
                shortArrayOf(0, 0, 0, 32768.toShort(), 32768.toShort(), 65535.toShort(), 65535.toShort(), 65535.toShort())
            } else {
                shortArrayOf(0, 16384, 32768.toShort(), 65535.toShort())
            }
        return VImage
            .newFromMemory(
                arena,
                arena.allocateFrom(ValueLayout.JAVA_SHORT, *samples),
                2,
                2,
                if (hasAlpha) 2 else 1,
                VipsBandFormat.FORMAT_USHORT.rawValue,
            ).copy(VipsOption.Enum(OPTION_INTERPRETATION, VipsInterpretation.INTERPRETATION_GREY16))
    }

    private fun floatImage(
        arena: Arena,
        channels: String,
    ): VImage {
        val grayscale = channels == "GRAY"
        val samples = if (grayscale) floatArrayOf(-1f, 2.9f, 300f, 128.75f) else floatArrayOf(-1f, 2.9f, 300f, 255.9f, 128.75f, 0f)
        return VImage
            .newFromMemory(
                arena,
                arena.allocateFrom(ValueLayout.JAVA_FLOAT, *samples),
                2,
                if (grayscale) 2 else 1,
                if (grayscale) 1 else 3,
                VipsBandFormat.FORMAT_FLOAT.rawValue,
            ).copy(
                VipsOption.Enum(
                    OPTION_INTERPRETATION,
                    if (grayscale) VipsInterpretation.INTERPRETATION_B_W else VipsInterpretation.INTERPRETATION_sRGB,
                ),
            )
    }

    private fun transformation(channels: String) =
        Transformation(
            width = 2.toDimension(),
            height = (if (channels == "GRAY") 2 else 1).toDimension(),
            format = ImageFormat.PIXELS,
            colorSpace = if (channels == "GRAY") ColorSpace.Grayscale else ColorSpace.SRGB,
            pixels = PixelTransformation(channels.toPixelChannels()),
        )

    private fun bytes(image: VImage): ByteArray {
        val buffer = image.writeToMemory().asByteBuffer()
        return ByteArray(buffer.remaining()).also { buffer.get(it) }
    }

    private suspend fun encode(
        arena: Arena,
        source: VImage,
        transformation: Transformation,
    ): ByteArray {
        val output = ByteChannel(autoFlush = true)
        try {
            RawPixelEncoder.writeToStream(arena, source, transformation, output)
            output.close()
            return output.readRemaining().readByteArray()
        } finally {
            output.cancel(null)
        }
    }
}
