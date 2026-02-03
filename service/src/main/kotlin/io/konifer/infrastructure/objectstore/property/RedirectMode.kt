package io.konifer.infrastructure.objectstore.property

enum class RedirectMode {
    NONE,
    PRESIGNED,
    BUCKET,
    CDN,
    ;

    companion object Factory {
        val default = NONE

        fun fromConfig(value: String): RedirectMode = valueOf(value.uppercase())
    }
}
