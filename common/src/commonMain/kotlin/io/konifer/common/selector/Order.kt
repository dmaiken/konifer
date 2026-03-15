package io.konifer.common.selector

enum class Order {
    NEW,
    MODIFIED,
    ;

    companion object {
        fun valueOfOrNull(value: String?): Order? =
            value?.let {
                try {
                    valueOf(it.uppercase())
                } catch (_: IllegalArgumentException) {
                    null
                }
            }
    }
}
