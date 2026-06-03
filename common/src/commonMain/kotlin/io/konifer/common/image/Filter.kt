package io.konifer.common.image

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private object FilterParameterValues {
    const val NONE = ""
    const val BLACK_WHITE = "black_white"
    const val GRAYSCALE = "grayscale"
    const val SEPIA = "sepia"
}

@Serializable
enum class Filter(
    override val queryParameterValue: String,
) : Manipulation {
    @SerialName(FilterParameterValues.NONE)
    NONE(FilterParameterValues.NONE),

    @SerialName(FilterParameterValues.BLACK_WHITE)
    BLACK_WHITE(FilterParameterValues.BLACK_WHITE),

    @SerialName(FilterParameterValues.GRAYSCALE)
    GRAYSCALE(FilterParameterValues.GRAYSCALE),

    @SerialName(FilterParameterValues.SEPIA)
    SEPIA(FilterParameterValues.SEPIA),
    ;

    companion object Factory {
        val default = NONE
    }
}
