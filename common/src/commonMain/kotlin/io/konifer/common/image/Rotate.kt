package io.konifer.common.image

enum class Rotate {
    ZERO,
    NINETY,
    ONE_HUNDRED_EIGHTY,
    TWO_HUNDRED_SEVENTY,
    AUTO,
    ;

    companion object Factory {
        val default = ZERO
    }
}
