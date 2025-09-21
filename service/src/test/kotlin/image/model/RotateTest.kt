package io.image.model

import io.asset.ManipulationParameters.ROTATE
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.http.ParametersBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

class RotateTest {

    companion object {
        @JvmStatic
        fun validSource() = listOf(
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
    fun `fromString valid arguments are accepted`(argument: String, rotation: Rotate) {
        Rotate.fromString(argument) shouldBe rotation
    }

    @ParameterizedTest
    @MethodSource("validSource")
    fun `fromParameters valid arguments are accepted`(argument: String, rotation: Rotate) {
        val parameters = ParametersBuilder().apply {
            set(ROTATE, argument)
        }.build()

        Rotate.fromQueryParameters(parameters, ROTATE) shouldBe rotation
    }

    @Test
    fun `fromParameters invalid rotation angle is rejected`() {
        val parameters = ParametersBuilder().apply {
            set(ROTATE, "91")
        }.build()

        val exception = shouldThrow<IllegalArgumentException> {
            Rotate.fromQueryParameters(parameters, ROTATE)
        }
        exception.message shouldBe "Invalid rotation: 91. Must be increments of 90"
    }

    @Test
    fun `fromString invalid rotation angle is rejected`() {
        val exception = shouldThrow<IllegalArgumentException> {
            Rotate.fromString("91")
        }
        exception.message shouldBe "Invalid rotation: 91. Must be increments of 90"
    }
}