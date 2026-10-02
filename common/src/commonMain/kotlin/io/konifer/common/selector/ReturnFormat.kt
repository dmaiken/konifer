package io.konifer.common.selector

const val DEFAULT_ENTRIES_LIMIT = 20

enum class ReturnFormat {
    CONTENT,
    INFO,
    ENTRIES,
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
