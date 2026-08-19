package io.konifer.infrastructure.vips.decode

import app.photofox.vipsffm.Vips
import io.konifer.ImageFactory
import io.konifer.common.image.Fit
import io.konifer.common.image.ImageFormat
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.variant.Transformation
import io.konifer.infrastructure.vips.transformer.Resize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.nio.file.Files
import java.nio.file.Path

class VipsThumbnailDecoderTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `uses standard decode when fit bounding box does not change effective dimensions`() {
        val image = ImageFactory.testImage(format = ImageFormat.PNG)
        val sourceFile = temporaryDirectory.resolve("source.png")
        Files.write(sourceFile, image.bytes)

        Vips.run { arena ->
            val decoded =
                VipsThumbnailDecoder.decode(
                    arena = arena,
                    sourceFile = sourceFile,
                    sourceFormat = ImageFormat.PNG,
                    transformation =
                        transformation(
                            width = image.attributes.width,
                            height = image.attributes.height + 100,
                            fit = Fit.FIT,
                        ),
                )

            decoded.image.width shouldBe image.attributes.width
            decoded.image.height shouldBe image.attributes.height
            decoded.appliedTransformations shouldBe emptyList()
            decoded.requiresLqipRegeneration shouldBe false
        }
    }

    @ParameterizedTest
    @EnumSource(ImageFormat::class)
    fun `reports resize and lqip impact applied by fill thumbnail`(format: ImageFormat) {
        val image = ImageFactory.testImage(format = format)
        val sourceFile = temporaryDirectory.resolve("source${format.extension}")
        Files.write(sourceFile, image.bytes)

        Vips.run { arena ->
            val decoded =
                VipsThumbnailDecoder.decode(
                    arena = arena,
                    sourceFile = sourceFile,
                    sourceFormat = format,
                    transformation = transformation(width = 224, height = 224, fit = Fit.FILL),
                )

            decoded.image.width shouldBe 224
            decoded.image.height shouldBe 224
            decoded.appliedTransformations.single().name shouldBe Resize.name
            decoded.requiresLqipRegeneration shouldBe true
        }
    }

    @ParameterizedTest
    @EnumSource(ImageFormat::class)
    fun `stretch thumbnail clamps dimensions when upscaling is disabled`(format: ImageFormat) {
        val image = ImageFactory.testImage(format = format)
        val sourceFile = temporaryDirectory.resolve("source${format.extension}")
        Files.write(sourceFile, image.bytes)

        Vips.run { arena ->
            val decoded =
                VipsThumbnailDecoder.decode(
                    arena = arena,
                    sourceFile = sourceFile,
                    sourceFormat = format,
                    transformation =
                        transformation(
                            width = image.attributes.width / 2,
                            height = image.attributes.height * 2,
                            fit = Fit.STRETCH,
                            canUpscale = false,
                        ),
                )

            decoded.image.width shouldBe image.attributes.width / 2
            decoded.image.height shouldBe image.attributes.height
            decoded.appliedTransformations.single().name shouldBe Resize.name
            decoded.requiresLqipRegeneration shouldBe true
        }
    }

    private fun transformation(
        width: Int,
        height: Int,
        fit: Fit,
        canUpscale: Boolean = true,
    ): Transformation =
        Transformation(
            width = width,
            height = height,
            fit = fit,
            canUpscale = canUpscale,
            format = ImageFormat.PNG,
            colorSpace = ColorSpace.SRGB,
        )
}
