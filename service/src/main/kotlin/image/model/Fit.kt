package io.image.model

import io.ktor.http.Parameters
import java.util.Locale.getDefault

enum class Fit {
    /**
     * fit within box, preserve aspect ratio, may leave empty padding.
     */
    SCALE,

    /**
     * fill box, crop overflow
     */
    FIT,

    /**
     * stretch to fit exactly, ignores aspect ratio.
     */
    STRETCH,

    ;

    companion object Factory {
        fun fromString(string: String?): Fit =
            string?.let {
                valueOf(string.uppercase(getDefault()))
            } ?: SCALE

        fun fromQueryParameters(
            parameters: Parameters,
            parameterName: String,
        ): Fit? =
            parameters[parameterName]?.let {
                valueOf(it.uppercase())
            }
    }
}
