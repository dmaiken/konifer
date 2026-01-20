package io.konifer.service.context.modifiers

enum class OrderBy {
    CREATED,
    MODIFIED,
    ;

    companion object {
        fun valueOfOrNull(value: String?): OrderBy? =
            value?.let {
                try {
                    valueOf(it.uppercase())
                } catch (_: IllegalArgumentException) {
                    null
                }
            }
    }
}
