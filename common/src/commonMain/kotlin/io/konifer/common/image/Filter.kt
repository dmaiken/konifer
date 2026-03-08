package io.konifer.common.image

enum class Filter {
    NONE,
    BLACK_WHITE,
    GREYSCALE,
    SEPIA,
    ;

    companion object Factory {
        val default = NONE
    }
}
