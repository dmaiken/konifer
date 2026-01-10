package io.direkt.infrastructure.vips

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import app.photofox.vipsffm.VipsOption
import app.photofox.vipsffm.enums.VipsAccess
import io.direkt.domain.image.ImageFormat
import io.kotest.matchers.collections.shouldBeOneOf
import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource

class AttributesFactoryTest {
    @ParameterizedTest
    @EnumSource(ImageFormat::class, mode = EnumSource.Mode.EXCLUDE)
    fun `non-paged images have correct attributes`(format: ImageFormat) {
        val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree${format.extension}")!!.readBytes()

        Vips.run { arena ->
            val vImage = VImage.newFromBytes(arena, image)

            val height = vImage.height
            val width = vImage.width
            val destinationFormat = ImageFormat.JPEG_XL

            val attributes =
                AttributesFactory.createAttributes(
                    image = vImage,
                    sourceFormat = format,
                    destinationFormat = destinationFormat,
                )
            attributes.format shouldBe destinationFormat
            attributes.height shouldBe height
            attributes.width shouldBe width
            attributes.pageCount shouldBeOneOf listOf(null, 1)
            attributes.loop shouldBeOneOf listOf(null, 0)
        }
    }

    @ParameterizedTest
    @MethodSource("io.direkt.domain.image.ImageTestSources#supportsPagedSource")
    fun `multi page gif images have correct attributes when being converted to format that supports paging`(
        destinationFormat: ImageFormat,
    ) {
        val image = javaClass.getResourceAsStream("/images/kermit/kermit.gif")!!.readBytes()

        Vips.run { arena ->
            // Interesting that if you don't specify n=-1, then you get nothing for page-height
            val vImage =
                VImage.newFromBytes(
                    arena,
                    image,
                    VipsOption.Int("n", -1),
                    VipsOption.Enum("access", VipsAccess.ACCESS_SEQUENTIAL),
                )

            val height = vImage.getInt(VipsOptionNames.OPTION_PAGE_HEIGHT)
            val width = vImage.width

            val attributes =
                AttributesFactory.createAttributes(
                    image = vImage,
                    sourceFormat = ImageFormat.GIF,
                    destinationFormat = destinationFormat,
                )
            attributes.format shouldBe destinationFormat
            attributes.height shouldBe height
            attributes.width shouldBe width
            attributes.pageCount shouldBe 19
            attributes.loop shouldBe 0
        }
    }

    @ParameterizedTest
    @MethodSource("io.direkt.domain.image.ImageTestSources#notSupportsPagedSource")
    fun `multi page gif images have correct attributes when being converted to format that does not support paging`(
        destinationFormat: ImageFormat,
    ) {
        val image = javaClass.getResourceAsStream("/images/kermit/kermit.gif")!!.readBytes()

        Vips.run { arena ->
            val vImage =
                VImage.newFromBytes(
                    arena,
                    image,
                    VipsOption.Int("n", 1),
                    VipsOption.Enum("access", VipsAccess.ACCESS_SEQUENTIAL),
                )

            val height = vImage.height
            val width = vImage.width

            val attributes =
                AttributesFactory.createAttributes(
                    image = vImage,
                    sourceFormat = ImageFormat.GIF,
                    destinationFormat = destinationFormat,
                )
            attributes.format shouldBe destinationFormat
            attributes.height shouldBe height
            attributes.width shouldBe width
            attributes.pageCount shouldBe null
            attributes.loop shouldBe null
        }
    }
}
