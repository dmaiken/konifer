package io.konifer.common.image

import kotlinx.serialization.SerialName

private object RotationParameterValues {
    const val ZERO = "0"
    const val NINETY = "90"
    const val ONE_HUNDRED_EIGHTY = "180"
    const val TWO_HUNDRED_SEVENTY = "270"
    const val AUTO = "auto"
}

enum class Rotate(
    override val queryParameterValue: String,
) : Manipulation {
    @SerialName(RotationParameterValues.ZERO)
    ZERO(RotationParameterValues.ZERO),

    @SerialName(RotationParameterValues.NINETY)
    NINETY(RotationParameterValues.NINETY),

    @SerialName(RotationParameterValues.ONE_HUNDRED_EIGHTY)
    ONE_HUNDRED_EIGHTY(RotationParameterValues.ONE_HUNDRED_EIGHTY),

    @SerialName(RotationParameterValues.TWO_HUNDRED_SEVENTY)
    TWO_HUNDRED_SEVENTY(RotationParameterValues.TWO_HUNDRED_SEVENTY),

    @SerialName(RotationParameterValues.AUTO)
    AUTO(RotationParameterValues.AUTO),
    ;

    companion object Factory {
        val default = ZERO
    }
}
