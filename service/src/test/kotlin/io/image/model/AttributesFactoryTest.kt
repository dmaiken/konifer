package io.image.model

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import app.photofox.vipsffm.VipsOption
import app.photofox.vipsffm.enums.VipsAccess
import io.image.AttributesFactory
import io.image.vips.VipsOptionNames.OPTION_PAGE_HEIGHT
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class AttributesFactoryTest {
    @ParameterizedTest
    @EnumSource(ImageFormat::class, mode = EnumSource.Mode.EXCLUDE, names = ["GIF"])
    fun `non-gif images have correct attributes`(format: ImageFormat) {
        val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree${format.extension}")!!.readBytes()

        Vips.run { arena ->
            val vImage = VImage.newFromBytes(arena, image)

            val height = vImage.height
            val width = vImage.width
            val destinationFormat = ImageFormat.JPEG_XL

            val attributes =
                AttributesFactory.createAttributes(
                    image = vImage,
                    destinationFormat = destinationFormat,
                )
            attributes.format shouldBe destinationFormat
            attributes.height shouldBe height
            attributes.width shouldBe width
            attributes.gif shouldBe null
        }
    }

    @Test
    fun `single page gif images have correct attributes`() {
        val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.gif")!!.readBytes()

        Vips.run { arena ->
            val vImage = VImage.newFromBytes(arena, image)

            val height = vImage.height
            val width = vImage.width
            val destinationFormat = ImageFormat.GIF

            val attributes =
                AttributesFactory.createAttributes(
                    image = vImage,
                    destinationFormat = destinationFormat,
                )
            attributes.format shouldBe destinationFormat
            attributes.height shouldBe height
            attributes.width shouldBe width
            attributes.gif shouldBe
                GifAttributes(
                    pages = 1,
                )
        }
    }

    @Test
    fun `multi page gif images have correct attributes`() {
        val image = javaClass.getResourceAsStream("/images/kermit.gif")!!.readBytes()

        Vips.run { arena ->
            // Interesting that if you don't specify n=-1, then you get nothing for page-height
            val vImage =
                VImage.newFromBytes(
                    arena,
                    image,
                    VipsOption.Int("n", -1),
                    VipsOption.Enum("access", VipsAccess.ACCESS_SEQUENTIAL),
                )

            val destinationFormat = ImageFormat.GIF
            val height = vImage.getInt(OPTION_PAGE_HEIGHT)
            val width = vImage.width

            val attributes =
                AttributesFactory.createAttributes(
                    image = vImage,
                    destinationFormat = destinationFormat,
                )
            attributes.format shouldBe destinationFormat
            attributes.height shouldBe height
            attributes.width shouldBe width
            attributes.gif shouldBe
                GifAttributes(
                    pages = 19,
                )
        }
    }

    @Test
    fun `multi page gif images have correct attributes when being converted to different format`() {
        val image = javaClass.getResourceAsStream("/images/kermit.gif")!!.readBytes()

        Vips.run { arena ->
            val vImage =
                VImage.newFromBytes(
                    arena,
                    image,
                    VipsOption.Int("n", 1),
                    VipsOption.Enum("access", VipsAccess.ACCESS_SEQUENTIAL),
                )

            val destinationFormat = ImageFormat.PNG
            val height = vImage.height
            val width = vImage.width

            val attributes =
                AttributesFactory.createAttributes(
                    image = vImage,
                    destinationFormat = destinationFormat,
                )
            attributes.format shouldBe destinationFormat
            attributes.height shouldBe height
            attributes.width shouldBe width
            attributes.gif shouldBe null
        }
    }
}
