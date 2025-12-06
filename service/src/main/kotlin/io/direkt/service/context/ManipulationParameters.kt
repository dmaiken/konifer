package io.direkt.service.context

object ManipulationParameters {
    const val HEIGHT = "h"
    const val WIDTH = "w"
    const val FIT = "fit"
    const val GRAVITY = "g"
    const val MIME_TYPE = "mimeType"
    const val ROTATE = "r"
    const val FLIP = "f"
    const val FILTER = "filter"
    const val BLUR = "blur"
    const val QUALITY = "q"
    const val PAD = "pad"
    const val BACKGROUND = "bg"

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
            MIME_TYPE,
            ROTATE,
            FLIP,
            FILTER,
            BLUR,
            QUALITY,
            PAD,
            BACKGROUND,
        )

    val ALL_RESERVED_PARAMETERS = ALL_TRANSFORMATION_PARAMETERS + setOf(VARIANT_PROFILE)
}
