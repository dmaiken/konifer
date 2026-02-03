package io.konifer.service.context.selector

object ManipulationParameters {
    const val HEIGHT = "h"
    const val WIDTH = "w"
    const val FIT = "fit"
    const val GRAVITY = "g"
    const val FORMAT = "format"
    const val ROTATE = "r"
    const val FLIP = "f"
    const val FILTER = "filter"
    const val BLUR = "blur"
    const val QUALITY = "q"
    const val PAD = "pad"
    const val PAD_COLOR = "pad-c"

    const val VARIANT_PROFILE = "profile"

    /**
     * All parameters minus [VARIANT_PROFILE]. New manipulation parameters must go in here for proper variant
     * request identification.
     */
    val ALL_TRANSFORMATION_PARAMETERS =
        setOf(
            HEIGHT,
            WIDTH,
            FIT,
            GRAVITY,
            FORMAT,
            ROTATE,
            FLIP,
            FILTER,
            BLUR,
            QUALITY,
            PAD,
            PAD_COLOR,
        )

    val ALL_RESERVED_PARAMETERS = ALL_TRANSFORMATION_PARAMETERS + setOf(VARIANT_PROFILE) + "s" + LIMIT_PARAMETER
}
