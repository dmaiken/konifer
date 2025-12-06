package io.direkt.service.context

fun String.toPositiveInt(): Int =
    this.toInt().also {
        if (it < 1) {
            throw IllegalArgumentException("Integer: $this must be positive")
        }
    }

fun String.toNonNegativeLong(): Long? =
    this.toLong().also {
        if (it < 0) {
            throw IllegalArgumentException("Long: $this must be greater than zero")
        }
    }
