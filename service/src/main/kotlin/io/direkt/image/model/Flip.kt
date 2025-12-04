package io.direkt.image.model

import io.ktor.http.Parameters

enum class Flip {
    /**
     * Horizontal
     */
    H,

    /**
     * Vertical
     */
    V,

    NONE,

    ;

    companion object Factory {
        val default = NONE

        fun fromString(string: String?): Flip =
            string?.let {
                valueOf(it.uppercase())
            } ?: default

        fun fromQueryParameters(
            parameters: Parameters,
            parameterName: String,
        ): Flip? = parameters[parameterName]?.let { valueOf(it.uppercase()) }
    }
}
