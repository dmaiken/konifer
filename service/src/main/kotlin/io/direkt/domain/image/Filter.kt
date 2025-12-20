package io.direkt.domain.image

import io.ktor.http.Parameters

enum class Filter {
    NONE,
    BLACK_WHITE,
    GREYSCALE,
    SEPIA,
    ;

    companion object Factory {
        val default = NONE

        fun fromString(string: String?): Filter =
            string?.let {
                valueOf(it.uppercase())
            } ?: Filter.default

        fun fromQueryParameters(
            parameters: Parameters,
            parameterName: String,
        ): Filter? = parameters[parameterName]?.let { valueOf(it.uppercase()) }
    }
}
