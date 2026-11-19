package io.konifer.common.image

import com.ghost.serialization.annotations.GhostSerialization
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private object FlipParameterValues {
    const val NONE = "none"
    const val HORIZONTAL = "h"
    const val VERTICAL = "v"
}

@Serializable
@GhostSerialization
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
