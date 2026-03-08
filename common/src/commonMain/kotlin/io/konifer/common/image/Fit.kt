package io.konifer.common.image

enum class Fit {
    /**
     * fit within box, preserve aspect ratio, may leave empty padding. Identical to CSS contain.
     */
    FIT,

    /**
     * fill box, crop overflow. Identical to CSS cover.
     */
    FILL,

    /**
     * stretch to fit exactly, ignores aspect ratio.
     */
    STRETCH,

    /**
     * using gravity value, crop image to height and width specified
     */
    CROP,
    ;

    companion object Factory {
        val default = FIT
    }
}
