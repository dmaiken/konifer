package io.konifer.common.image

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private object GravityParameterValues {
    const val CENTER = "center"
    const val ENTROPY = "entropy"
    const val ATTENTION = "attention"
}

/**
 * This is ignored if the [Fit] is not [Fit.CROP] or [Fit.FILL]
 */
@Serializable
enum class Gravity(
    override val queryParameterValue: String,
) : Manipulation {
    @SerialName(GravityParameterValues.CENTER)
    CENTER(GravityParameterValues.CENTER),

    @SerialName(GravityParameterValues.ENTROPY)
    ENTROPY(GravityParameterValues.ENTROPY),

    @SerialName(GravityParameterValues.ATTENTION)
    ATTENTION(GravityParameterValues.ATTENTION),
    ;

    companion object Factory {
        val default = CENTER
    }
}
