package io.konifer.common.image

/**
 * This is ignored if the [Fit] is not [Fit.CROP] or [Fit.FILL]
 */
enum class Gravity {
    CENTER,
    ENTROPY,
    ATTENTION,
    ;

    companion object Factory {
        val default = CENTER
    }
}
