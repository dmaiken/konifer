package io.konifer.domain.context

import io.konifer.common.image.Fit
import io.konifer.common.image.ImageFormat
import io.konifer.createRequestedImageTransformation
import io.konifer.domain.transformation.toDimension
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
        requested.height shouldBe 200.toDimension()
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
        normalized.width shouldBe 200.toDimension()
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
                padColor = badBackground,
                format = ImageFormat.PNG,
            )
        }.message shouldBe "Pad color must be a hex value starting with '#'"
    }
}
