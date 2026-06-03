package io.konifer.domain.context

fun String.toNonNegativeLong(): Long? =
    this.toLong().also {
        if (it < 0) {
            throw IllegalArgumentException("Long: $this must be greater than zero")
        }
    }
