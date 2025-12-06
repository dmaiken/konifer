package io.direkt.domain.image

import io.ktor.http.Parameters

enum class Rotate {
    ZERO,
    NINETY,
    ONE_HUNDRED_EIGHTY,
    TWO_HUNDRED_SEVENTY,
    AUTO,
    ;

    companion object Factory {
        val default = ZERO

        fun fromString(string: String?): Rotate =
            string?.let {
                toRotate(it)
            } ?: default

        fun fromQueryParameters(
            parameters: Parameters,
            parameterName: String,
        ): Rotate? =
            parameters[parameterName]?.let {
                toRotate(it)
            }

        private fun toRotate(value: String): Rotate =
            value.toIntOrNull()?.let {
                when (it) {
                    0 -> ZERO
                    90 -> NINETY
                    180 -> ONE_HUNDRED_EIGHTY
                    270 -> TWO_HUNDRED_SEVENTY
                    else -> throw IllegalArgumentException("Invalid rotation: $value. Must be increments of 90")
                }
            } ?: valueOf(value.uppercase())
    }
}
