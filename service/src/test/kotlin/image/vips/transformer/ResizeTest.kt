package io.image.vips.transformer

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import app.photofox.vipsffm.VipsOption
import app.photofox.vipsffm.enums.VipsInteresting
import app.photofox.vipsffm.enums.VipsSize
import image.model.ImageFormat
import io.PHash
import io.asset.AssetStreamContainer
import io.image.model.Fit
import io.image.model.Gravity
import io.image.vips.VImageFactory
import io.image.vips.VipsOption.VIPS_OPTION_CROP
import io.image.vips.VipsOption.VIPS_OPTION_HEIGHT
import io.image.vips.VipsOption.VIPS_OPTION_INTERESTING
import io.image.vips.VipsOption.VIPS_OPTION_SIZE
import io.image.vips.aspectRatio
import io.image.vips.transformation.Resize
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.ktor.utils.io.ByteReadChannel
import io.matcher.shouldBeApproximately
import io.matcher.shouldBeWithinOneOf
import io.matcher.shouldHaveSamePixelContentAs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class ResizeTest {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Test
    fun `upscaling will not happen if not enabled`() {
        val image =
            javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.use {
                it.readBytes()
            }
        val imageChannel = ByteReadChannel(image)
        val container = AssetStreamContainer.fromReadChannel(scope, imageChannel)
        Vips.run { arena ->
            val source = VImageFactory.newFromContainer(arena, container)
            val processedImage =
                Resize(
                    width = 10000,
                    height = null,
                    fit = Fit.FIT,
                    upscale = false,
                    gravity = Gravity.CENTER,
                ).transform(arena, source)

            processedImage.width shouldBe source.width
            processedImage.height shouldBe source.height
        }
    }

    @ParameterizedTest
    @EnumSource(Fit::class)
    fun `if height and width are not supplied then no resizing occurs`(fit: Fit) {
        val image =
            javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.use {
                it.readBytes()
            }
        val imageChannel = ByteReadChannel(image)
        val container = AssetStreamContainer.fromReadChannel(scope, imageChannel)
        Vips.run { arena ->
            val source = VImageFactory.newFromContainer(arena, container)
            val processedImage =
                Resize(
                    width = null,
                    height = null,
                    fit = fit,
                    upscale = false,
                    gravity = Gravity.CENTER,
                ).transform(arena, source)

            processedImage.width shouldBe source.width
            processedImage.height shouldBe source.height
        }
    }

    @Nested
    inner class FitResizeTests {
        @ParameterizedTest
        @EnumSource(ImageFormat::class)
        fun `can resize down with fit mode`(format: ImageFormat) {
            val height = 50
            val width = 50
            val image =
                javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.${format.extension}")!!.use {
                    it.readBytes()
                }
            val imageChannel = ByteReadChannel(image)
            val container = AssetStreamContainer.fromReadChannel(scope, imageChannel)
            Vips.run { arena ->
                val source = VImageFactory.newFromContainer(arena, container)
                val processedImage =
                    Resize(
                        width = width,
                        height = height,
                        fit = Fit.FIT,
                        upscale = true,
                        gravity = Gravity.CENTER,
                    ).transform(arena, source)

                (processedImage.height == height || processedImage.width == width) shouldBe true
                processedImage.aspectRatio() shouldBeApproximately source.aspectRatio()

                val processedStream = ByteArrayOutputStream()
                processedImage.writeToStream(processedStream, ".${format.extension}")

                PHash.hammingDistance(image, processedStream.toByteArray()) shouldBeLessThan HAMMING_DISTANCE_CEILING
            }
        }

        @ParameterizedTest
        @EnumSource(ImageFormat::class)
        fun `can resize up with fit mode`(format: ImageFormat) {
            val height = 2000
            val width = 2000
            val image =
                javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.${format.extension}")!!.use {
                    it.readBytes()
                }
            val imageChannel = ByteReadChannel(image)
            val container = AssetStreamContainer.fromReadChannel(scope, imageChannel)
            Vips.run { arena ->
                val source = VImageFactory.newFromContainer(arena, container)
                val processedImage =
                    Resize(
                        width = width,
                        height = height,
                        fit = Fit.FIT,
                        upscale = true,
                        gravity = Gravity.CENTER,
                    ).transform(arena, source)

                (processedImage.height == height || processedImage.width == width) shouldBe true
                processedImage.aspectRatio() shouldBeApproximately source.aspectRatio()

                val processedStream = ByteArrayOutputStream()
                processedImage.writeToStream(processedStream, ".${format.extension}")

                PHash.hammingDistance(image, processedStream.toByteArray()) shouldBeLessThan HAMMING_DISTANCE_CEILING
            }
        }

        @Test
        fun `can resize by height only with fit mode`() =
            runTest {
                val height = 200
                val image =
                    javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.use {
                        it.readBytes()
                    }
                val imageChannel = ByteReadChannel(image)
                val container = AssetStreamContainer.fromReadChannel(scope, imageChannel)
                Vips.run { arena ->
                    val source = VImageFactory.newFromContainer(arena, container)
                    val processedImage =
                        Resize(
                            width = null,
                            height = height,
                            fit = Fit.FIT,
                            upscale = true,
                            gravity = Gravity.CENTER,
                        ).transform(arena, source)

                    processedImage.height shouldBe height
                    processedImage.aspectRatio() shouldBeApproximately source.aspectRatio()
                }
            }

        @Test
        fun `can resize by width only with fit mode`() {
            val width = 200
            val image =
                javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.use {
                    it.readBytes()
                }
            val imageChannel = ByteReadChannel(image)
            val container = AssetStreamContainer.fromReadChannel(scope, imageChannel)
            Vips.run { arena ->
                val source = VImageFactory.newFromContainer(arena, container)
                val processedImage =
                    Resize(
                        width = width,
                        height = null,
                        fit = Fit.FIT,
                        upscale = true,
                        gravity = Gravity.CENTER,
                    ).transform(arena, source)

                processedImage.width shouldBeWithinOneOf width
                processedImage.aspectRatio() shouldBeApproximately source.aspectRatio()
            }
        }
    }

    @Nested
    inner class FillResizeTests {
        @ParameterizedTest
        @EnumSource(ImageFormat::class)
        fun `can resize down with fill mode`(format: ImageFormat) {
            val height = 50
            val width = 50
            val image =
                javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.${format.extension}")!!.use {
                    it.readBytes()
                }
            val imageChannel = ByteReadChannel(image)
            val container = AssetStreamContainer.fromReadChannel(scope, imageChannel)
            Vips.run { arena ->
                val source = VImageFactory.newFromContainer(arena, container)
                val processedImage =
                    Resize(
                        width = width,
                        height = height,
                        fit = Fit.FILL,
                        upscale = true,
                        gravity = Gravity.CENTER,
                    ).transform(arena, source)

                processedImage.height shouldBe height
                processedImage.width shouldBe width

                val processedStream = ByteArrayOutputStream()
                processedImage.writeToStream(processedStream, ".${format.extension}")

                PHash.hammingDistance(image, processedStream.toByteArray()) shouldBeGreaterThan HAMMING_DISTANCE_CEILING
            }
        }

        @ParameterizedTest
        @EnumSource(ImageFormat::class)
        fun `can resize up with fill mode`(format: ImageFormat) {
            val height = 2000
            val width = 2000
            val image =
                javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.${format.extension}")!!.use {
                    it.readBytes()
                }
            val imageChannel = ByteReadChannel(image)
            val container = AssetStreamContainer.fromReadChannel(scope, imageChannel)
            Vips.run { arena ->
                val source = VImageFactory.newFromContainer(arena, container)
                val processedImage =
                    Resize(
                        width = width,
                        height = height,
                        fit = Fit.FILL,
                        upscale = true,
                        gravity = Gravity.CENTER,
                    ).transform(arena, source)

                processedImage.height shouldBe height
                processedImage.width shouldBe width

                val processedStream = ByteArrayOutputStream()
                processedImage.writeToStream(processedStream, ".${format.extension}")

                PHash.hammingDistance(image, processedStream.toByteArray()) shouldBeGreaterThan HAMMING_DISTANCE_CEILING
            }
        }

        @ParameterizedTest
        @EnumSource(Gravity::class)
        fun `can apply gravity to fill mode`(gravity: Gravity) {
            val height = 200
            val width = 200
            val image =
                javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.use {
                    it.readBytes()
                }
            val interesting =
                when (gravity) {
                    Gravity.CENTER -> VipsInteresting.INTERESTING_CENTRE
                    Gravity.ATTENTION -> VipsInteresting.INTERESTING_ATTENTION
                    Gravity.ENTROPY -> VipsInteresting.INTERESTING_ENTROPY
                }
            Vips.run { arena ->
                val expected =
                    VImage.newFromBytes(arena, image)
                        .thumbnailImage(
                            width,
                            VipsOption.Int(VIPS_OPTION_HEIGHT, height),
                            VipsOption.Enum(VIPS_OPTION_CROP, interesting),
                            VipsOption.Enum(VIPS_OPTION_SIZE, VipsSize.SIZE_BOTH),
                        )
                val transformed =
                    Resize(
                        width = width,
                        height = height,
                        fit = Fit.FILL,
                        upscale = true,
                        gravity = gravity,
                    ).transform(arena, VImage.newFromBytes(arena, image))

                transformed.height shouldBe height
                transformed.width shouldBe width

                val expectedStream = ByteArrayOutputStream()
                expected.writeToStream(expectedStream, ".jpeg")
                val actualStream = ByteArrayOutputStream()
                transformed.writeToStream(actualStream, ".jpeg")

                val expectedImage = ImageIO.read(ByteArrayInputStream(expectedStream.toByteArray()))
                val actualImage = ImageIO.read(ByteArrayInputStream(actualStream.toByteArray()))

                actualImage shouldHaveSamePixelContentAs expectedImage
            }
        }
    }

    @Nested
    inner class StretchResizeTests {
        @ParameterizedTest
        @EnumSource(ImageFormat::class)
        fun `can resize down with stretch mode`(format: ImageFormat) {
            val height = 50
            val width = 50
            val image =
                javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.${format.extension}")!!.use {
                    it.readBytes()
                }
            val imageChannel = ByteReadChannel(image)
            val container = AssetStreamContainer.fromReadChannel(scope, imageChannel)
            Vips.run { arena ->
                val source = VImageFactory.newFromContainer(arena, container)
                val processedImage =
                    Resize(
                        width = width,
                        height = height,
                        fit = Fit.STRETCH,
                        upscale = true,
                        gravity = Gravity.CENTER,
                    ).transform(arena, source)

                processedImage.height shouldBe height
                processedImage.width shouldBe width

                val processedStream = ByteArrayOutputStream()
                processedImage.writeToStream(processedStream, ".${format.extension}")

                PHash.hammingDistance(image, processedStream.toByteArray()) shouldBeLessThan HAMMING_DISTANCE_CEILING
            }
        }

        @ParameterizedTest
        @EnumSource(ImageFormat::class)
        fun `can resize up with stretch mode`(format: ImageFormat) {
            val height = 2000
            val width = 2000
            val image =
                javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.${format.extension}")!!.use {
                    it.readBytes()
                }
            val imageChannel = ByteReadChannel(image)
            val container = AssetStreamContainer.fromReadChannel(scope, imageChannel)
            Vips.run { arena ->
                val source = VImageFactory.newFromContainer(arena, container)
                val processedImage =
                    Resize(
                        width = width,
                        height = height,
                        fit = Fit.STRETCH,
                        upscale = true,
                        gravity = Gravity.CENTER,
                    ).transform(arena, source)

                processedImage.height shouldBe height
                processedImage.width shouldBe width

                val processedStream = ByteArrayOutputStream()
                processedImage.writeToStream(processedStream, ".${format.extension}")

                PHash.hammingDistance(image, processedStream.toByteArray()) shouldBeLessThan HAMMING_DISTANCE_CEILING
            }
        }
    }

    @Nested
    inner class CropResizeTests {
        @ParameterizedTest
        @EnumSource(Gravity::class)
        fun `can resize with crop mode and gravity`(gravity: Gravity) {
            val height = 500
            val width = 500
            val image =
                javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.use {
                    it.readBytes()
                }
            val interesting =
                when (gravity) {
                    Gravity.CENTER -> VipsInteresting.INTERESTING_CENTRE
                    Gravity.ATTENTION -> VipsInteresting.INTERESTING_ATTENTION
                    Gravity.ENTROPY -> VipsInteresting.INTERESTING_ENTROPY
                }
            val expectedStream = ByteArrayOutputStream()
            val actualStream = ByteArrayOutputStream()
            Vips.run { arena ->
                val expected =
                    VImage.newFromBytes(arena, image).smartcrop(
                        width,
                        height,
                        VipsOption.Enum(VIPS_OPTION_INTERESTING, interesting),
                    )
                val transformed =
                    Resize(
                        width = width,
                        height = height,
                        fit = Fit.CROP,
                        upscale = true,
                        gravity = gravity,
                    ).transform(arena, VImage.newFromBytes(arena, image))

                transformed.height shouldBe height
                transformed.width shouldBe width

                expected.writeToStream(expectedStream, ".png")
                transformed.writeToStream(actualStream, ".png")

                val expectedImage = ImageIO.read(ByteArrayInputStream(expectedStream.toByteArray()))
                val actualImage = ImageIO.read(ByteArrayInputStream(actualStream.toByteArray()))

                actualImage shouldHaveSamePixelContentAs expectedImage
            }
        }
    }

    @Nested
    inner class LQIPRegenerationTests {
        @ParameterizedTest
        @EnumSource(Fit::class, mode = EnumSource.Mode.INCLUDE, names = ["FILL", "STRETCH"])
        fun `lqip regeneration is required if scaling down with certain fits`(fit: Fit) {
            val image =
                javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.use {
                    it.readBytes()
                }
            val imageChannel = ByteReadChannel(image)
            val container = AssetStreamContainer.fromReadChannel(scope, imageChannel)
            Vips.run { arena ->
                val source = VImageFactory.newFromContainer(arena, container)
                val transformer =
                    Resize(
                        width = 50,
                        height = 50,
                        fit = fit,
                        upscale = true,
                        gravity = Gravity.CENTER,
                    )
                transformer.requiresLqipRegeneration(source) shouldBe true
            }
        }

        @ParameterizedTest
        @EnumSource(Fit::class, mode = EnumSource.Mode.INCLUDE, names = ["FILL", "STRETCH"])
        fun `lqip regeneration is required if scaling up with certain fits`(fit: Fit) {
            val image =
                javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.use {
                    it.readBytes()
                }
            val imageChannel = ByteReadChannel(image)
            val container = AssetStreamContainer.fromReadChannel(scope, imageChannel)
            Vips.run { arena ->
                val source = VImageFactory.newFromContainer(arena, container)
                val transformer =
                    Resize(
                        width = 2000,
                        height = 2000,
                        fit = fit,
                        upscale = true,
                        gravity = Gravity.CENTER,
                    )
                transformer.requiresLqipRegeneration(source) shouldBe true
            }
        }

        @ParameterizedTest
        @EnumSource(Fit::class, mode = EnumSource.Mode.INCLUDE, names = ["FILL", "STRETCH"])
        fun `lqip regeneration is not required if scaling up with certain fits but upscaling is disabled`(fit: Fit) {
            val image =
                javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.use {
                    it.readBytes()
                }
            val imageChannel = ByteReadChannel(image)
            val container = AssetStreamContainer.fromReadChannel(scope, imageChannel)
            Vips.run { arena ->
                val source = VImageFactory.newFromContainer(arena, container)
                val transformer =
                    Resize(
                        width = 2000,
                        height = 2000,
                        fit = fit,
                        upscale = false,
                        gravity = Gravity.CENTER,
                    )
                transformer.requiresLqipRegeneration(source) shouldBe false
            }
        }

        @Test
        fun `lqip regeneration is not required if scaling up with scale fit`() {
            val image =
                javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.use {
                    it.readBytes()
                }
            val imageChannel = ByteReadChannel(image)
            val container = AssetStreamContainer.fromReadChannel(scope, imageChannel)
            Vips.run { arena ->
                val source = VImageFactory.newFromContainer(arena, container)
                val transformer =
                    Resize(
                        width = 2000,
                        height = 2000,
                        fit = Fit.FIT,
                        upscale = true,
                        gravity = Gravity.CENTER,
                    )
                transformer.requiresLqipRegeneration(source) shouldBe false
            }
        }

        @Test
        fun `lqip regeneration is not required if scaling down with scale fit`() {
            val image =
                javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.use {
                    it.readBytes()
                }
            val imageChannel = ByteReadChannel(image)
            val container = AssetStreamContainer.fromReadChannel(scope, imageChannel)
            Vips.run { arena ->
                val source = VImageFactory.newFromContainer(arena, container)
                val transformer =
                    Resize(
                        width = 50,
                        height = 50,
                        fit = Fit.FIT,
                        upscale = true,
                        gravity = Gravity.CENTER,
                    )
                transformer.requiresLqipRegeneration(source) shouldBe false
            }
        }
    }
}
