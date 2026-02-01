package io.konifer.service.context.selector

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
