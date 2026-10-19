package io.konifer.common.image

import io.konifer.common.image.ManipulationParameters.ALL_TRANSFORMATION_PARAMETERS
import io.konifer.common.image.ManipulationParameters.VARIANT_PROFILE

object ManipulationParameters {
    const val HEIGHT = "height"
    const val WIDTH = "width"
    const val FIT = "fit"
    const val GRAVITY = "gravity"
    const val FORMAT = "format"
    const val ROTATE = "rotate"
    const val FLIP = "flip"
    const val FILTER = "filter"
    const val BLUR = "blur"
    const val QUALITY = "quality"
    const val PAD = "pad"
    const val PAD_COLOR = "pad-color"
    const val STRIP = "strip"
    const val COLOR_SPACE = "color-space"

    const val VARIANT_PROFILE = "profile"

    const val PIXEL_CHANNELS = "pixel-channels"

    const val PIXEL_LAYOUT = "pixel-layout"

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
            STRIP,
            COLOR_SPACE,
            PIXEL_CHANNELS,
            PIXEL_LAYOUT,
        )
}

const val LIMIT_PARAMETER = "limit"

val ALL_RESERVED_PARAMETERS = ALL_TRANSFORMATION_PARAMETERS + VARIANT_PROFILE + "s" + LIMIT_PARAMETER
