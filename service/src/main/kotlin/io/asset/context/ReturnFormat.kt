package io.asset.context

enum class ReturnFormat {
    CONTENT,
    METADATA,
    REDIRECT,
    DOWNLOAD,
    LINK,
    ;

    companion object {
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
