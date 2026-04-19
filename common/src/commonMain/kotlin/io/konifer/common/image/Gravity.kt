package io.konifer.common.image

/**
 * This is ignored if the [Fit] is not [Fit.CROP] or [Fit.FILL]
 */
enum class Gravity(
    override val queryParameterValue: String,
) : Manipulation {
    CENTER("center"),
    ENTROPY("entropy"),
    ATTENTION("attention"),
    ;

    companion object Factory {
        val default = CENTER
    }
}
