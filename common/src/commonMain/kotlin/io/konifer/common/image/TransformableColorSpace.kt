package io.konifer.common.image

enum class TransformableColorSpace(
    override val queryParameterValue: String,
) : Manipulation {
    /**
     * Retain the existing colorspace embedded in the ICC profile within the image.
     */
    ORIGIN("origin"),
    SRGB("srgb"),
    P3("p3"),
    GRAYSCALE("grayscale"),
    ;

    companion object Factory {
        val default = ORIGIN
    }
}
