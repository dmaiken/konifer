package io.konifer.infrastructure.vips.decode

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import io.konifer.ImageFactory
import io.konifer.common.image.Fit
import io.konifer.common.image.ImageFormat
import io.konifer.common.image.Rotate
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.transformation.Transformation
import io.konifer.domain.transformation.toDimension
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_ORIENTATION
import io.konifer.infrastructure.vips.transformer.AutoRotate
import io.konifer.infrastructure.vips.transformer.Resize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString

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

    @Test
    fun `auto-rotation plans resize using oriented dimensions`() {
        val image = ImageFactory.testImage(format = ImageFormat.JPEG)
        val sourceFile = temporaryDirectory.resolve("auto-rotate.jpeg")

        Vips.run { arena ->
            VImage
                .newFromBytes(arena, image.bytes)
                .set(OPTION_ORIENTATION, 6)
                .writeToFile(sourceFile.absolutePathString())

            val decoded =
                VipsThumbnailDecoder.decode(
                    arena = arena,
                    sourceFile = sourceFile,
                    sourceFormat = ImageFormat.JPEG,
                    transformation =
                        transformation(
                            width = 301,
                            height = 400,
                            fit = Fit.FIT,
                            rotate = Rotate.TWO_HUNDRED_SEVENTY,
                            isAutoRotate = true,
                        ),
                )

            decoded.image.width shouldBe 301
            decoded.image.height shouldBe 400
            decoded.appliedTransformations.map { it.name } shouldBe
                listOf(Resize.name, AutoRotate.name)
            decoded.requiresLqipRegeneration shouldBe true
        }
    }

    @Test
    fun `ordinary thumbnail does not report auto-rotation`() {
        val image = ImageFactory.testImage(format = ImageFormat.JPEG)
        val sourceFile = temporaryDirectory.resolve("source.jpeg")
        Files.write(sourceFile, image.bytes)

        Vips.run { arena ->
            val decoded =
                VipsThumbnailDecoder.decode(
                    arena = arena,
                    sourceFile = sourceFile,
                    sourceFormat = ImageFormat.JPEG,
                    transformation = transformation(width = 400, height = 301, fit = Fit.FIT),
                )

            decoded.appliedTransformations.map { it.name } shouldBe listOf(Resize.name)
            decoded.requiresLqipRegeneration shouldBe false
        }
    }

    private fun transformation(
        width: Int,
        height: Int,
        fit: Fit,
        canUpscale: Boolean = true,
        rotate: Rotate = Rotate.ZERO,
        horizontalFlip: Boolean = false,
        isAutoRotate: Boolean = false,
    ): Transformation =
        Transformation(
            width = width.toDimension(),
            height = height.toDimension(),
            fit = fit,
            canUpscale = canUpscale,
            format = ImageFormat.PNG,
            colorSpace = ColorSpace.SRGB,
            rotate = rotate,
            horizontalFlip = horizontalFlip,
            isAutoRotate = isAutoRotate,
        )
}
