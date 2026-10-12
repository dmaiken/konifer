package io.konifer.infrastructure.vips.encode

import io.konifer.common.image.ImageFormat
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class VipsEncoderSelectorTest {
    @Test
    fun `selects raw pixel encoder for pixels`() {
        VipsEncoderSelector.getEncoder(ImageFormat.PIXELS) shouldBeSameInstanceAs RawPixelEncoder
    }

    @Test
    fun `selects JPEG XL encoder for JPEG XL`() {
        VipsEncoderSelector.getEncoder(ImageFormat.JPEG_XL) shouldBeSameInstanceAs VipsJpegXlEncoder
    }

    @ParameterizedTest
    @EnumSource(ImageFormat::class, mode = EnumSource.Mode.EXCLUDE, names = ["PIXELS", "JPEG_XL"])
    fun `selects standard encoder for other formats`(format: ImageFormat) {
        VipsEncoderSelector.getEncoder(format) shouldBeSameInstanceAs StandardVipsEncoder
    }
}
