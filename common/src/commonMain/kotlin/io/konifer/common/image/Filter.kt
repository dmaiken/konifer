package io.konifer.common.image

enum class Filter(
    override val queryParameterValue: String,
) : Manipulation {
    NONE(""),
    BLACK_WHITE("black_white"),
    GREYSCALE("greyscale"),
    SEPIA("sepia"),
    ;

    companion object Factory {
        val default = NONE
    }
}
