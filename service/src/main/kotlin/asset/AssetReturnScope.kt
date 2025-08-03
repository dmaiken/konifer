package io.asset

enum class AssetReturnScope {
    SINGLE,
    SHALLOW,
    RECURSIVE,
    ;

    companion object Factory {
        fun fromQueryParam(param: String?): AssetReturnScope =
            param?.let {
                valueOf(it.uppercase())
            } ?: SINGLE
    }
}
