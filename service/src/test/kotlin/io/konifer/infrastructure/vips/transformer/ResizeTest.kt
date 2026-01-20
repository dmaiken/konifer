package io.konifer.infrastructure.vips.transformer

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import app.photofox.vipsffm.VipsOption
import app.photofox.vipsffm.enums.VipsAccess
import app.photofox.vipsffm.enums.VipsInteresting
import app.photofox.vipsffm.enums.VipsSize
import io.konifer.PHash
import io.konifer.domain.asset.AssetDataContainer
import io.konifer.domain.image.Fit
import io.konifer.domain.image.Gravity
import io.konifer.domain.image.ImageFormat
import io.konifer.domain.variant.Transformation
import io.konifer.infrastructure.vips.VImageFactory
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_CROP
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_HEIGHT
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_INTERESTING
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_SIZE
import io.konifer.infrastructure.vips.aspectRatio
import io.konifer.infrastructure.vips.transformation.Resize
import io.konifer.matchers.shouldBeApproximately
import io.konifer.matchers.shouldBeWithinOneOf
import io.konifer.matchers.shouldHaveSamePixelContentAs
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class ResizeTest {
    @Test
    fun `upscaling will not happen if not enabled`() {
        val image =
            javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.use {
                it.readBytes()
            }
        val imageChannel = ByteReadChannel(image)
        AssetDataContainer(imageChannel).use { container ->
            Vips.run { arena ->
                val source = VImageFactory.newFromContainer(arena, container)

                val processedImage =
                    Resize.transform(
                        arena = arena,
                        source = source,
                        transformation =
                            resizeTransformation(
                                width = 10000,
                                height = 3000,
                                upscale = false,
                            ),
                    )

                processedImage.processed.width shouldBe source.width
                processedImage.processed.height shouldBe source.height
            }
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
        AssetDataContainer(imageChannel).use { container ->
            Vips.run { arena ->
                val source = VImageFactory.newFromContainer(arena, container)
                val processedImage =
                    Resize.transform(
                        arena = arena,
                        source = source,
                        transformation =
                            resizeTransformation(
                                width = source.width,
                                height = source.height,
                                fit = fit,
                            ),
                    )

                processedImage.processed.width shouldBe source.width
                processedImage.processed.height shouldBe source.height
            }
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
                javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree${format.extension}")!!.use {
                    it.readBytes()
                }
            val imageChannel = ByteReadChannel(image)
            AssetDataContainer(imageChannel).use { container ->
                Vips.run { arena ->
                    val source = VImageFactory.newFromContainer(arena, container)
                    val processedImage =
                        Resize.transform(
                            arena = arena,
                            source = source,
                            transformation =
                                resizeTransformation(
                                    width = width,
                                    height = height,
                                    fit = Fit.FIT,
                                ),
                        )

                    (processedImage.processed.height == height || processedImage.processed.width == width) shouldBe true
                    processedImage.processed.aspectRatio() shouldBeApproximately source.aspectRatio()

                    val processedStream = ByteArrayOutputStream()
                    processedImage.processed.writeToStream(processedStream, format.extension)

                    PHash.hammingDistance(image, processedStream.toByteArray()) shouldBeLessThan HAMMING_DISTANCE_CEILING
                }
            }
        }

        @ParameterizedTest
        @EnumSource(ImageFormat::class)
        fun `can resize up with fit mode`(format: ImageFormat) {
            val height = 2000
            val width = 2000
            val image =
                javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree${format.extension}")!!.use {
                    it.readBytes()
                }
            val imageChannel = ByteReadChannel(image)
            AssetDataContainer(imageChannel).use { container ->
                Vips.run { arena ->
                    val source = VImageFactory.newFromContainer(arena, container)
                    val processedImage =
                        Resize.transform(
                            arena = arena,
                            source = source,
                            transformation =
                                resizeTransformation(
                                    width = width,
                                    height = height,
                                    fit = Fit.FIT,
                                ),
                        )

                    (processedImage.processed.height == height || processedImage.processed.width == width) shouldBe true
                    processedImage.processed.aspectRatio() shouldBeApproximately source.aspectRatio()

                    val processedStream = ByteArrayOutputStream()
                    processedImage.processed.writeToStream(processedStream, format.extension)

                    PHash.hammingDistance(image, processedStream.toByteArray()) shouldBeLessThan HAMMING_DISTANCE_CEILING
                }
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
                AssetDataContainer(imageChannel).use { container ->
                    Vips.run { arena ->
                        val source = VImageFactory.newFromContainer(arena, container)
                        val processedImage =
                            Resize.transform(
                                arena = arena,
                                source = source,
                                transformation =
                                    resizeTransformation(
                                        width = source.width,
                                        height = height,
                                        fit = Fit.FIT,
                                    ),
                            )

                        processedImage.processed.height shouldBe height
                        processedImage.processed.aspectRatio() shouldBeApproximately source.aspectRatio()
                    }
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
            AssetDataContainer(imageChannel).use { container ->
                Vips.run { arena ->
                    val source = VImageFactory.newFromContainer(arena, container)
                    val processedImage =
                        Resize.transform(
                            arena = arena,
                            source = source,
                            transformation =
                                resizeTransformation(
                                    width = width,
                                    height = source.height,
                                    fit = Fit.FIT,
                                ),
                        )

                    processedImage.processed.width shouldBeWithinOneOf width
                    processedImage.processed.aspectRatio() shouldBeApproximately source.aspectRatio()
                }
            }
        }

        @ParameterizedTest
        @MethodSource("io.konifer.domain.image.ImageTestSources#supportsPagedSource")
        fun `can resize multi page image`(format: ImageFormat) {
            val width = 200
            val image =
                javaClass.getResourceAsStream("/images/kermit/kermit${format.extension}")!!.use {
                    it.readBytes()
                }
            val imageChannel = ByteReadChannel(image)
            AssetDataContainer(imageChannel).use { container ->
                val decoderOptions =
                    arrayOf<VipsOption>(
                        VipsOption.Int("n", -1),
                        VipsOption.Enum("access", VipsAccess.ACCESS_SEQUENTIAL),
                    )
                val output = ByteArrayOutputStream()
                Vips.run { arena ->
                    val source = VImageFactory.newFromContainer(arena, container, decoderOptions)
                    val processedImage =
                        Resize.transform(
                            arena = arena,
                            source = source,
                            transformation =
                                resizeTransformation(
                                    width = width,
                                    height = source.height,
                                    fit = Fit.FIT,
                                ),
                        )

                    processedImage.processed.width shouldBeWithinOneOf width
                    processedImage.processed.aspectRatio() shouldBeApproximately source.aspectRatio()
                    processedImage.processed.writeToStream(output, format.extension)
                }
                val outputBytes = output.toByteArray()
                PHash.hammingDistance(image, outputBytes) shouldBeLessThan HAMMING_DISTANCE_IDENTICAL
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
                javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree${format.extension}")!!.use {
                    it.readBytes()
                }
            val imageChannel = ByteReadChannel(image)
            AssetDataContainer(imageChannel).use { container ->
                Vips.run { arena ->
                    val source = VImageFactory.newFromContainer(arena, container)
                    val processedImage =
                        Resize.transform(
                            arena = arena,
                            source = source,
                            transformation =
                                resizeTransformation(
                                    width = width,
                                    height = height,
                                    fit = Fit.FILL,
                                ),
                        )

                    processedImage.processed.height shouldBe height
                    processedImage.processed.width shouldBe width

                    val processedStream = ByteArrayOutputStream()
                    processedImage.processed.writeToStream(processedStream, format.extension)

                    PHash.hammingDistance(image, processedStream.toByteArray()) shouldBeGreaterThan HAMMING_DISTANCE_CEILING
                }
            }
        }

        @ParameterizedTest
        @EnumSource(ImageFormat::class)
        fun `can resize up with fill mode`(format: ImageFormat) {
            val height = 2000
            val width = 2000
            val image =
                javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree${format.extension}")!!.use {
                    it.readBytes()
                }
            val imageChannel = ByteReadChannel(image)
            AssetDataContainer(imageChannel).use { container ->
                Vips.run { arena ->
                    val source = VImageFactory.newFromContainer(arena, container)
                    val processedImage =
                        Resize.transform(
                            arena = arena,
                            source = source,
                            transformation =
                                resizeTransformation(
                                    width = width,
                                    height = height,
                                    fit = Fit.FILL,
                                ),
                        )

                    processedImage.processed.height shouldBe height
                    processedImage.processed.width shouldBe width

                    val processedStream = ByteArrayOutputStream()
                    processedImage.processed.writeToStream(processedStream, format.extension)

                    PHash.hammingDistance(image, processedStream.toByteArray()) shouldBeGreaterThan HAMMING_DISTANCE_CEILING
                }
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
                    VImage
                        .newFromBytes(arena, image)
                        .thumbnailImage(
                            width,
                            VipsOption.Int(OPTION_HEIGHT, height),
                            VipsOption.Enum(OPTION_CROP, interesting),
                            VipsOption.Enum(OPTION_SIZE, VipsSize.SIZE_BOTH),
                        )
                val transformed =
                    Resize.transform(
                        arena = arena,
                        source = VImage.newFromBytes(arena, image),
                        transformation =
                            resizeTransformation(
                                width = width,
                                height = height,
                                fit = Fit.FILL,
                                gravity = gravity,
                            ),
                    )

                transformed.processed.height shouldBe height
                transformed.processed.width shouldBe width

                val expectedStream = ByteArrayOutputStream()
                expected.writeToStream(expectedStream, ".jpeg")
                val actualStream = ByteArrayOutputStream()
                transformed.processed.writeToStream(actualStream, ".jpeg")

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
                javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree${format.extension}")!!.use {
                    it.readBytes()
                }
            val imageChannel = ByteReadChannel(image)
            AssetDataContainer(imageChannel).use { container ->
                Vips.run { arena ->
                    val source = VImageFactory.newFromContainer(arena, container)
                    val processedImage =
                        Resize.transform(
                            arena = arena,
                            source = source,
                            transformation =
                                resizeTransformation(
                                    width = width,
                                    height = height,
                                    fit = Fit.STRETCH,
                                ),
                        )

                    processedImage.processed.height shouldBe height
                    processedImage.processed.width shouldBe width

                    val processedStream = ByteArrayOutputStream()
                    processedImage.processed.writeToStream(processedStream, format.extension)

                    PHash.hammingDistance(image, processedStream.toByteArray()) shouldBeLessThan HAMMING_DISTANCE_CEILING
                }
            }
        }

        @ParameterizedTest
        @EnumSource(ImageFormat::class)
        fun `can resize up with stretch mode`(format: ImageFormat) {
            val height = 2000
            val width = 2000
            val image =
                javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree${format.extension}")!!.use {
                    it.readBytes()
                }
            val imageChannel = ByteReadChannel(image)
            AssetDataContainer(imageChannel).use { container ->
                Vips.run { arena ->
                    val source = VImageFactory.newFromContainer(arena, container)
                    val processedImage =
                        Resize.transform(
                            arena = arena,
                            source = source,
                            transformation =
                                resizeTransformation(
                                    width = width,
                                    height = height,
                                    fit = Fit.STRETCH,
                                ),
                        )

                    processedImage.processed.height shouldBe height
                    processedImage.processed.width shouldBe width

                    val processedStream = ByteArrayOutputStream()
                    processedImage.processed.writeToStream(processedStream, format.extension)

                    PHash.hammingDistance(image, processedStream.toByteArray()) shouldBeLessThan HAMMING_DISTANCE_CEILING
                }
            }
        }

        @ParameterizedTest
        @MethodSource("io.konifer.domain.image.ImageTestSources#supportsPagedSource")
        fun `can resize multi page gif with stretch mode`(format: ImageFormat) {
            val height = 200
            val width = 200
            val image =
                javaClass.getResourceAsStream("/images/kermit/kermit${format.extension}")!!.use {
                    it.readBytes()
                }
            val imageChannel = ByteReadChannel(image)
            AssetDataContainer(imageChannel).use { container ->
                val decoderOptions =
                    arrayOf<VipsOption>(
                        VipsOption.Int("n", -1),
                        VipsOption.Enum("access", VipsAccess.ACCESS_SEQUENTIAL),
                    )
                val output = ByteArrayOutputStream()
                Vips.run { arena ->
                    val source = VImageFactory.newFromContainer(arena, container, decoderOptions)
                    val processedImage =
                        Resize.transform(
                            arena = arena,
                            source = source,
                            transformation =
                                resizeTransformation(
                                    width = width,
                                    height = height,
                                    fit = Fit.STRETCH,
                                ),
                        )
                    processedImage.processed.writeToStream(output, format.extension)
                }
                val outputBytes = output.toByteArray()
                PHash.hammingDistance(image, outputBytes) shouldBeLessThan HAMMING_DISTANCE_CEILING
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
                        VipsOption.Enum(OPTION_INTERESTING, interesting),
                    )
                val transformed =
                    Resize.transform(
                        arena = arena,
                        source = VImage.newFromBytes(arena, image),
                        transformation =
                            resizeTransformation(
                                width = width,
                                height = height,
                                fit = Fit.CROP,
                                gravity = gravity,
                            ),
                    )

                transformed.processed.height shouldBe height
                transformed.processed.width shouldBe width

                expected.writeToStream(expectedStream, ".png")
                transformed.processed.writeToStream(actualStream, ".png")

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
            AssetDataContainer(imageChannel).use { container ->
                Vips.run { arena ->
                    val source = VImageFactory.newFromContainer(arena, container)
                    Resize
                        .transform(
                            arena = arena,
                            source = source,
                            transformation =
                                resizeTransformation(
                                    width = 50,
                                    height = 50,
                                    fit = fit,
                                ),
                        ).requiresLqipRegeneration shouldBe true
                }
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
            AssetDataContainer(imageChannel).use { container ->
                Vips.run { arena ->
                    val source = VImageFactory.newFromContainer(arena, container)
                    Resize
                        .transform(
                            arena = arena,
                            source = source,
                            transformation =
                                resizeTransformation(
                                    width = 2000,
                                    height = 2000,
                                    fit = fit,
                                ),
                        ).requiresLqipRegeneration shouldBe true
                }
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
            AssetDataContainer(imageChannel).use { container ->
                Vips.run { arena ->
                    val source = VImageFactory.newFromContainer(arena, container)
                    Resize
                        .transform(
                            arena = arena,
                            source = source,
                            transformation =
                                resizeTransformation(
                                    width = 2000,
                                    height = 2000,
                                    fit = fit,
                                    upscale = false,
                                ),
                        ).requiresLqipRegeneration shouldBe false
                }
            }
        }

        @Test
        fun `lqip regeneration is not required if scaling up with fit mode of FIT`() {
            val image =
                javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.use {
                    it.readBytes()
                }
            val imageChannel = ByteReadChannel(image)
            AssetDataContainer(imageChannel).use { container ->
                Vips.run { arena ->
                    val source = VImageFactory.newFromContainer(arena, container)
                    Resize
                        .transform(
                            arena = arena,
                            source = source,
                            transformation =
                                resizeTransformation(
                                    width = 2000,
                                    height = 2000,
                                    fit = Fit.FIT,
                                ),
                        ).requiresLqipRegeneration shouldBe false
                }
            }
        }

        @Test
        fun `lqip regeneration is not required if scaling down with scale fit`() {
            val image =
                javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.use {
                    it.readBytes()
                }
            val imageChannel = ByteReadChannel(image)
            AssetDataContainer(imageChannel).use { container ->
                Vips.run { arena ->
                    val source = VImageFactory.newFromContainer(arena, container)
                    Resize
                        .transform(
                            arena = arena,
                            source = source,
                            transformation =
                                resizeTransformation(
                                    width = 50,
                                    height = 50,
                                    fit = Fit.FIT,
                                ),
                        ).requiresLqipRegeneration shouldBe false
                }
            }
        }
    }

    @Nested
    inner class RequiresTransformationTests {
        @Test
        fun `transformation not required if source dimensions match transformation dimensions`() {
            val image =
                javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.use {
                    it.readBytes()
                }
            val imageChannel = ByteReadChannel(image)
            AssetDataContainer(imageChannel).use { container ->
                Vips.run { arena ->
                    val source = VImageFactory.newFromContainer(arena, container)
                    Resize.requiresTransformation(
                        arena = arena,
                        source = source,
                        transformation =
                            resizeTransformation(
                                width = source.width,
                                height = source.height,
                            ),
                    ) shouldBe false
                }
            }
        }

        @Test
        fun `transformation required if source width does not match transformation width`() {
            val image =
                javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.use {
                    it.readBytes()
                }
            val imageChannel = ByteReadChannel(image)
            AssetDataContainer(imageChannel).use { container ->
                Vips.run { arena ->
                    val source = VImageFactory.newFromContainer(arena, container)
                    Resize.requiresTransformation(
                        arena = arena,
                        source = source,
                        transformation =
                            resizeTransformation(
                                width = source.width + 1,
                                height = source.height,
                            ),
                    ) shouldBe true
                }
            }
        }

        @Test
        fun `transformation required if source height does not match transformation height`() {
            val image =
                javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.use {
                    it.readBytes()
                }
            val imageChannel = ByteReadChannel(image)
            AssetDataContainer(imageChannel).use { container ->
                Vips.run { arena ->
                    val source = VImageFactory.newFromContainer(arena, container)
                    Resize.requiresTransformation(
                        arena = arena,
                        source = source,
                        transformation =
                            resizeTransformation(
                                width = source.width,
                                height = source.height + 1,
                            ),
                    ) shouldBe true
                }
            }
        }
    }

    private fun resizeTransformation(
        width: Int,
        height: Int,
        upscale: Boolean = true,
        fit: Fit = Fit.default,
        gravity: Gravity = Gravity.default,
    ) = Transformation(
        height = height,
        width = width,
        canUpscale = upscale,
        format = ImageFormat.PNG,
        fit = fit,
        gravity = gravity,
    )
}
