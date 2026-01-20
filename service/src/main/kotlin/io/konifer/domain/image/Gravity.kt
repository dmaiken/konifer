package io.konifer.domain.image

import io.ktor.http.Parameters
import java.util.Locale.getDefault

/**
 * This is ignored if the [Fit] is not [Fit.CROP] or [Fit.FILL]
 */
enum class Gravity {
    CENTER,
    ENTROPY,
    ATTENTION,
    ;

    companion object Factory {
        val default = CENTER

        fun fromString(string: String?): Gravity =
            string?.let {
                valueOf(string.uppercase(getDefault()))
            } ?: default

        fun fromQueryParameters(
            parameters: Parameters,
            parameterName: String,
        ): Gravity? =
            parameters[parameterName]?.let {
                valueOf(it.uppercase())
            }
    }
}
