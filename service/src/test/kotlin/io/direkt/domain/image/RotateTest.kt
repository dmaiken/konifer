package io.direkt.domain.image

import io.direkt.domain.image.Rotate
import io.direkt.service.context.ManipulationParameters.ROTATE
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.http.ParametersBuilder
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource

class RotateTest {
    companion object {
        @JvmStatic
        fun validSource() =
            listOf(
                arguments("0", Rotate.ZERO),
                arguments("90", Rotate.NINETY),
                arguments("180", Rotate.ONE_HUNDRED_EIGHTY),
                arguments("270", Rotate.TWO_HUNDRED_SEVENTY),
                arguments("auto", Rotate.AUTO),
                arguments("AUTO", Rotate.AUTO),
            )
    }

    @ParameterizedTest
    @MethodSource("validSource")
    fun `fromString valid arguments are accepted`(
        argument: String,
        rotation: Rotate,
    ) {
        Rotate.fromString(argument) shouldBe rotation
    }

    @ParameterizedTest
    @MethodSource("validSource")
    fun `fromParameters valid arguments are accepted`(
        argument: String,
        rotation: Rotate,
    ) {
        val parameters =
            ParametersBuilder()
                .apply {
                    set(ROTATE, argument)
                }.build()

        Rotate.fromQueryParameters(parameters, ROTATE) shouldBe rotation
    }

    @ParameterizedTest
    @ValueSource(strings = ["-90", "91", "-180", "-270", "1"])
    fun `fromParameters invalid rotation angle is rejected`(invalid: String) {
        val parameters =
            ParametersBuilder()
                .apply {
                    set(ROTATE, invalid)
                }.build()

        val exception =
            shouldThrow<IllegalArgumentException> {
                Rotate.fromQueryParameters(parameters, ROTATE)
            }
        exception.message shouldBe "Invalid rotation: $invalid. Must be increments of 90"
    }

    @ParameterizedTest
    @ValueSource(strings = ["-90", "91", "-180", "-270", "1"])
    fun `fromString invalid rotation angle is rejected`(invalid: String) {
        val exception =
            shouldThrow<IllegalArgumentException> {
                Rotate.fromString(invalid)
            }
        exception.message shouldBe "Invalid rotation: $invalid. Must be increments of 90"
    }
}
