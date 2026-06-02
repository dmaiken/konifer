package io.konifer.common.image

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private object FlipParameterValues {
    const val NONE = ""
    const val HORIZONTAL = "h"
    const val VERTICAL = "v"
}

@Serializable
enum class Flip(
    override val queryParameterValue: String,
) : Manipulation {
    /**
     * Horizontal
     */
    @SerialName(FlipParameterValues.HORIZONTAL)
    H(FlipParameterValues.HORIZONTAL),

    /**
     * Vertical
     */
    @SerialName(FlipParameterValues.VERTICAL)
    V(FlipParameterValues.VERTICAL),

    @SerialName(FlipParameterValues.NONE)
    NONE(FlipParameterValues.NONE),

    ;

    companion object Factory {
        val default = NONE
    }
}
