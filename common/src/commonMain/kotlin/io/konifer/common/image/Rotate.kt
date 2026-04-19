package io.konifer.common.image

enum class Rotate(
    override val queryParameterValue: String,
) : Manipulation {
    ZERO("0"),
    NINETY("90"),
    ONE_HUNDRED_EIGHTY("180"),
    TWO_HUNDRED_SEVENTY("270"),
    AUTO("auto"),
    ;

    companion object Factory {
        val default = ZERO
    }
}
