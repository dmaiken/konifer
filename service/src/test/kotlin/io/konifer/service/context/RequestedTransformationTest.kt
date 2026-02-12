package io.konifer.service.context

import io.konifer.createRequestedImageTransformation
import io.konifer.domain.image.Fit
import io.konifer.domain.image.ImageFormat
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource

class RequestedTransformationTest {
    @Test
    fun `only height is required when using scale fit`() {
        val requested =
            shouldNotThrowAny {
                createRequestedImageTransformation(
                    height = 200,
                    format = ImageFormat.PNG,
                    fit = Fit.FIT,
                )
            }
        requested.fit shouldBe Fit.FIT
        requested.height shouldBe 200
        requested.format shouldBe ImageFormat.PNG
    }

    @Test
    fun `only width is required when using scale fit`() {
        val normalized =
            shouldNotThrowAny {
                createRequestedImageTransformation(
                    width = 200,
                    format = ImageFormat.PNG,
                    fit = Fit.FIT,
                )
            }
        normalized.fit shouldBe Fit.FIT
        normalized.width shouldBe 200
        normalized.format shouldBe ImageFormat.PNG
    }

    @ParameterizedTest
    @EnumSource(Fit::class, mode = EnumSource.Mode.EXCLUDE, names = ["FIT"])
    fun `height and width are required depending on the fit`(fit: Fit) {
        shouldThrow<IllegalArgumentException> {
            createRequestedImageTransformation(
                height = 200,
                format = ImageFormat.PNG,
                fit = fit,
            )
        }.message shouldBe "Height or width must be supplied for fit: $fit"
    }

    @Test
    fun `width cannot be less than 1 if supplied`() {
        shouldThrow<IllegalArgumentException> {
            createRequestedImageTransformation(
                width = 0,
            )
        }.message shouldBe "Width cannot be < 1"
    }

    @Test
    fun `height cannot be less than 1 if supplied`() {
        shouldThrow<IllegalArgumentException> {
            createRequestedImageTransformation(
                height = 0,
            )
        }.message shouldBe "Height cannot be < 1"
    }

    @ParameterizedTest
    @ValueSource(
        ints = [
            -1, 151,
        ],
    )
    fun `blur cannot be outside bounds if supplied`(blur: Int) {
        shouldThrow<IllegalArgumentException> {
            createRequestedImageTransformation(
                blur = blur,
            )
        }.message shouldBe "Blur must be between 0 and 150"
    }

    @ParameterizedTest
    @ValueSource(
        ints = [
            0, 101,
        ],
    )
    fun `quality cannot be outside bounds if supplied`(quality: Int) {
        shouldThrow<IllegalArgumentException> {
            createRequestedImageTransformation(
                quality = quality,
            )
        }.message shouldBe "Quality must be between 1 and 100"
    }

    @Test
    fun `pad cannot be negative`() {
        shouldThrow<IllegalArgumentException> {
            createRequestedImageTransformation(
                pad = -1,
            )
        }.message shouldBe "Pad must not be negative"
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "#", "", " ", "FFFFFF", "##",
        ],
    )
    fun `throws when normalizing invalidBackground`(badBackground: String) {
        shouldThrow<IllegalArgumentException> {
            createRequestedImageTransformation(
                pad = 10,
                background = badBackground,
                format = ImageFormat.PNG,
            )
        }.message shouldBe "Pad color must be a hex value starting with '#'"
    }
}
