package io.konifer.common.image

enum class Fit(
    override val queryParameterValue: String,
) : Manipulation {
    /**
     * fit within box, preserve aspect ratio, may leave empty padding. Identical to CSS contain.
     */
    FIT("fit"),

    /**
     * fill box, crop overflow. Identical to CSS cover.
     */
    FILL("fill"),

    /**
     * stretch to fit exactly, ignores aspect ratio.
     */
    STRETCH("stretch"),

    /**
     * using gravity value, crop image to height and width specified
     */
    CROP("crop"),
    ;

    companion object Factory {
        val default = FIT
    }
}
