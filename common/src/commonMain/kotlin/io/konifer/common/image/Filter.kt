package io.konifer.common.image

enum class Filter(
    override val queryParameterValue: String,
) : Manipulation {
    NONE(""),
    BLACK_WHITE("black_white"),
    GRAYSCALE("grayscale"),
    SEPIA("sepia"),
    ;

    companion object Factory {
        val default = NONE
    }
}
