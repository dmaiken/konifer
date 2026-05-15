package io.konifer.infrastructure.vips

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import io.konifer.common.image.ImageFormat
import io.konifer.domain.image.ColorSpace
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Named
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource

class ImageColorSpaceExtractorTest {
    companion object {
        @JvmStatic
        fun iccProfileSource() =
            listOf(
                Arguments.arguments(Named.named("srgb", "/images/metadata/exif-xmp-iptc.jpg"), ColorSpace.SRGB),
                Arguments.arguments(Named.named("p3", "/images/metadata/iphone-p3.jpg"), ColorSpace.P3),
                Arguments.arguments(Named.named("adobe_rgb", "/images/metadata/adobe-rgb.jpg"), ColorSpace.AdobeRGB),
            )
    }

    @ParameterizedTest
    @MethodSource("iccProfileSource")
    fun `can extract icc profile`(
        filePath: String,
        colorspace: ColorSpace,
    ) {
        Vips.run { arena ->
            val image = javaClass.getResourceAsStream(filePath)!!.readBytes()
            val source = VImage.newFromBytes(arena, image)

            val metadata = ImageColorSpaceExtractor.extract(source)

            metadata shouldBe colorspace
        }
    }

    @Test
    fun `returns interpretation if image has no icc profile`() {
        Vips.run { arena ->
            val image = javaClass.getResourceAsStream("/images/metadata/stripped-all.jpg")!!.readBytes()
            val source = VImage.newFromBytes(arena, image)

            val metadata = ImageColorSpaceExtractor.extract(source)

            metadata shouldBe ColorSpace.SRGB
        }
    }

    @Test
    fun `returns greyscale if image is grayscale`() {
        Vips.run { arena ->
            val image = javaClass.getResourceAsStream("/images/colorspace/gray.jpeg")!!.readBytes()
            val source = VImage.newFromBytes(arena, image)

            val metadata = ImageColorSpaceExtractor.extract(source)

            metadata shouldBe ColorSpace.Grayscale
        }
    }

    @ParameterizedTest
    @EnumSource(ImageFormat::class)
    fun `can extract colorspace of all formats`(format: ImageFormat) {
        Vips.run { arena ->
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree${format.extension}")!!.readBytes()
            val source = VImage.newFromBytes(arena, image)

            val metadata = ImageColorSpaceExtractor.extract(source)

            metadata shouldBe ColorSpace.SRGB
        }
    }
}
