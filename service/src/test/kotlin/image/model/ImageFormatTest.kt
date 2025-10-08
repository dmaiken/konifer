package io.image.model

import image.model.ImageFormat
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class ImageFormatTest {
    companion object {
        @JvmStatic
        fun supportedImageFormatSource(): Stream<Arguments> =
            Stream.of(
                Arguments.arguments("jpg", ImageFormat.JPEG),
                Arguments.arguments("jpeg", ImageFormat.JPEG),
                Arguments.arguments("png", ImageFormat.PNG),
                Arguments.arguments("webp", ImageFormat.WEBP),
                Arguments.arguments("avif", ImageFormat.AVIF),
            )

        @JvmStatic
        fun supportedImageMimeTypeSource(): Stream<Arguments> =
            Stream.of(
                Arguments.arguments("image/jpeg", ImageFormat.JPEG),
                Arguments.arguments("image/png", ImageFormat.PNG),
                Arguments.arguments("image/webp", ImageFormat.WEBP),
                Arguments.arguments("image/avif", ImageFormat.AVIF),
            )
    }

    @ParameterizedTest
    @MethodSource("supportedImageFormatSource")
    fun `test fromFormat with supported image type`(
        string: String,
        expected: ImageFormat,
    ) {
        ImageFormat.fromFormat(string) shouldBe expected
    }

    @Test
    fun `test fromFormat with unsupported image type`() {
        val exception =
            shouldThrow<IllegalArgumentException> {
                ImageFormat.fromFormat("unsupported")
            }
        exception.message shouldBe "Unsupported image format: unsupported"
    }

    @ParameterizedTest
    @MethodSource("supportedImageMimeTypeSource")
    fun `test from mimeType with supported mimeType`(
        mimeType: String,
        expected: ImageFormat,
    ) {
        ImageFormat.fromMimeType(mimeType) shouldBe expected
    }

    @Test
    fun `test fromMimeType with unsupported image type`() {
        val exception =
            shouldThrow<IllegalArgumentException> {
                ImageFormat.fromMimeType("unsupported")
            }
        exception.message shouldBe "Unsupported image mime type: unsupported"
    }
}
