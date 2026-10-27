package io.konifer.domain.context

fun String.toNonNegativeLong(): Long? = toLongOrNull()?.takeIf { it >= 0 }
