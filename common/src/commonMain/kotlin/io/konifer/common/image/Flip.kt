package io.konifer.common.image

enum class Flip(
    override val queryParameterValue: String,
) : Manipulation {
    /**
     * Horizontal
     */
    H("h"),

    /**
     * Vertical
     */
    V("v"),

    NONE(""),

    ;

    companion object Factory {
        val default = NONE
    }
}
