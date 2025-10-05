package io.asset

import io.asset.ManipulationParameters.VARIANT_PROFILE

object ManipulationParameters {
    const val HEIGHT = "h"
    const val WIDTH = "w"
    const val FIT = "fit"
    const val GRAVITY = "g"
    const val MIME_TYPE = "mimeType"
    const val ROTATE = "r"
    const val FLIP = "f"
    const val FILTER = "filter"

    const val VARIANT_PROFILE = "profile"

    /**
     * All parameters minus [VARIANT_PROFILE]. New manipulation parameters must go in here for proper variant
     * request identification.
     */
    val ALL_PARAMETERS =
        setOf(
            HEIGHT,
            WIDTH,
            FIT,
            GRAVITY,
            MIME_TYPE,
            ROTATE,
            FLIP,
            FILTER,
        )
}
