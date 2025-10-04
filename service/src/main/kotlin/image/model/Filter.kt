package io.image.model

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
                Filter.valueOf(it.uppercase())
            } ?: Filter.default

        fun fromQueryParameters(
            parameters: Parameters,
            parameterName: String,
        ): Filter? = parameters[parameterName]?.let { Filter.valueOf(it.uppercase()) }
    }
}
