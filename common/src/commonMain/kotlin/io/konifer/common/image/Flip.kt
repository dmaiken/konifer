package io.konifer.common.image

enum class Flip {
    /**
     * Horizontal
     */
    H,

    /**
     * Vertical
     */
    V,

    NONE,

    ;

    companion object Factory {
        val default = NONE
    }
}
