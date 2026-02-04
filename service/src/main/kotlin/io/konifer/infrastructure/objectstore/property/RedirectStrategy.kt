package io.konifer.infrastructure.objectstore.property

enum class RedirectStrategy {
    NONE,
    PRESIGNED,
    TEMPLATE,
    ;

    companion object Factory {
        val default = NONE

        fun fromConfig(value: String): RedirectStrategy = valueOf(value.uppercase())
    }
}
