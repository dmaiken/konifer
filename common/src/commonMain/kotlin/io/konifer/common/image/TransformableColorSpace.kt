package io.konifer.common.image

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private object TransformableColorSpaceParameterValues {
    const val ORIGIN = "origin"
    const val SRGB = "srgb"
    const val P3 = "p3"
    const val GRAYSCALE = "grayscale"
}

@Serializable
enum class TransformableColorSpace(
    override val queryParameterValue: String,
) : Manipulation {
    /**
     * Retain the existing colorspace embedded in the ICC profile within the image.
     */
    @SerialName(TransformableColorSpaceParameterValues.ORIGIN)
    ORIGIN(TransformableColorSpaceParameterValues.ORIGIN),

    @SerialName(TransformableColorSpaceParameterValues.SRGB)
    SRGB(TransformableColorSpaceParameterValues.SRGB),

    @SerialName(TransformableColorSpaceParameterValues.P3)
    P3(TransformableColorSpaceParameterValues.P3),

    @SerialName(TransformableColorSpaceParameterValues.GRAYSCALE)
    GRAYSCALE(TransformableColorSpaceParameterValues.GRAYSCALE),
    ;

    companion object Factory {
        val default = ORIGIN
    }
}
