package io.konifer.common.image

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private object FitParameterValues {
    const val FIT = "fit"
    const val FILL = "fill"
    const val STRETCH = "stretch"
    const val CROP = "crop"
}

@Serializable
enum class Fit(
    override val queryParameterValue: String,
) : Manipulation {
    /**
     * Fit within a box, preserve the aspect ratio, may leave empty padding. Identical to CSS contain.
     */
    @SerialName(FitParameterValues.FIT)
    FIT(FitParameterValues.FIT),

    /**
     * Fill box, crop overflow. Identical to CSS cover.
     */
    @SerialName(FitParameterValues.FILL)
    FILL(FitParameterValues.FILL),

    /**
     * Stretch to fit exactly, ignores the aspect ratio.
     */
    @SerialName(FitParameterValues.STRETCH)
    STRETCH(FitParameterValues.STRETCH),

    /**
     * Using gravity value, crop the image to the height and width specified
     */
    @SerialName(FitParameterValues.CROP)
    CROP(FitParameterValues.CROP),
    ;

    companion object Factory {
        val default = FIT
    }
}
