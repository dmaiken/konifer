package io.asset.context

enum class ReturnFormat {
    CONTENT,
    METADATA,
    REDIRECT,
    LINK,
    ;

    companion object {
        fun fromQueryParam(param: String?) =
            param?.let {
                valueOf(it.uppercase())
            } ?: LINK // Default

        fun valueOfOrNull(value: String?): ReturnFormat? =
            value?.let {
                try {
                    valueOf(it.uppercase())
                } catch (_: IllegalArgumentException) {
                    null
                }
            }
    }
}
