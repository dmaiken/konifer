package io.konifer.common.selector

enum class ReturnFormat {
    CONTENT,
    INFO,
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
