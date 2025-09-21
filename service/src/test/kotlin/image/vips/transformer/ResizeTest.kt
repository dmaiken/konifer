package io.image.vips.transformer

import app.photofox.vipsffm.Vips
import image.model.ImageFormat
import io.PHash
import io.asset.AssetStreamContainer
import io.image.model.Fit
import io.image.vips.VImageFactory
import io.image.vips.aspectRatio
import io.image.vips.transformation.Resize
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.ktor.utils.io.ByteReadChannel
import io.matcher.shouldBeApproximately
import io.matcher.shouldBeWithinOneOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.ByteArrayOutputStream

class ResizeTest {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        @BeforeAll
        @JvmStatic
        fun beforeAll() {
            Vips.init()
        }
    }

    @ParameterizedTest
    @EnumSource(ImageFormat::class)
    fun `can resize down with scale mode`(format: ImageFormat) {
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
                    fit = Fit.SCALE,
                    upscale = true,
                ).transform(source)

            (processedImage.height == height || processedImage.width == width) shouldBe true
            processedImage.aspectRatio() shouldBeApproximately source.aspectRatio()

            val processedStream = ByteArrayOutputStream()
            processedImage.writeToStream(processedStream, ".${format.extension}")

            PHash.hammingDistance(image, processedStream.toByteArray()) shouldBeLessThan HAMMING_DISTANCE_CEILING
        }
    }

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
                ).transform(source)

            processedImage.height shouldBe height
            processedImage.width shouldBe width

            val processedStream = ByteArrayOutputStream()
            processedImage.writeToStream(processedStream, ".${format.extension}")

            PHash.hammingDistance(image, processedStream.toByteArray()) shouldBeGreaterThan HAMMING_DISTANCE_CEILING
        }
    }

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
                ).transform(source)

            processedImage.height shouldBe height
            processedImage.width shouldBe width

            val processedStream = ByteArrayOutputStream()
            processedImage.writeToStream(processedStream, ".${format.extension}")

            PHash.hammingDistance(image, processedStream.toByteArray()) shouldBeLessThan HAMMING_DISTANCE_CEILING
        }
    }

    @ParameterizedTest
    @EnumSource(ImageFormat::class)
    fun `can resize up with scale mode`(format: ImageFormat) {
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
                    fit = Fit.SCALE,
                    upscale = true,
                ).transform(source)

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
                ).transform(source)

            processedImage.height shouldBe height
            processedImage.width shouldBe width

            val processedStream = ByteArrayOutputStream()
            processedImage.writeToStream(processedStream, ".${format.extension}")

            PHash.hammingDistance(image, processedStream.toByteArray()) shouldBeGreaterThan HAMMING_DISTANCE_CEILING
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
                ).transform(source)

            processedImage.height shouldBe height
            processedImage.width shouldBe width

            val processedStream = ByteArrayOutputStream()
            processedImage.writeToStream(processedStream, ".${format.extension}")

            PHash.hammingDistance(image, processedStream.toByteArray()) shouldBeLessThan HAMMING_DISTANCE_CEILING
        }
    }

    @Test
    fun `can resize by height only with scale mode`() =
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
                        fit = Fit.SCALE,
                        upscale = true,
                    ).transform(source)

                processedImage.height shouldBe height
                processedImage.aspectRatio() shouldBeApproximately source.aspectRatio()
            }
        }

    @Test
    fun `can resize by width only with scale mode`() {
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
                    fit = Fit.SCALE,
                    upscale = true,
                ).transform(source)

            processedImage.width shouldBeWithinOneOf width
            processedImage.aspectRatio() shouldBeApproximately source.aspectRatio()
        }
    }

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
                    fit = Fit.SCALE,
                    upscale = false,
                ).transform(source)

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
                ).transform(source)

            processedImage.width shouldBe source.width
            processedImage.height shouldBe source.height
        }
    }

    @ParameterizedTest
    @EnumSource(Fit::class, mode = EnumSource.Mode.INCLUDE, names = ["FIT", "STRETCH"])
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
                )
            transformer.requiresLqipRegeneration(source) shouldBe true
        }
    }

    @ParameterizedTest
    @EnumSource(Fit::class, mode = EnumSource.Mode.INCLUDE, names = ["FIT", "STRETCH"])
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
                )
            transformer.requiresLqipRegeneration(source) shouldBe true
        }
    }

    @ParameterizedTest
    @EnumSource(Fit::class, mode = EnumSource.Mode.INCLUDE, names = ["FIT", "STRETCH"])
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
                    fit = Fit.SCALE,
                    upscale = true,
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
                    fit = Fit.SCALE,
                    upscale = true,
                )
            transformer.requiresLqipRegeneration(source) shouldBe false
        }
    }
}
