package io.konifer.domain.context

@JvmInline
value class Limit(
    val value: Int,
) {
    init {
        require(value > 0 || value == -1) {
            "Limit must be -1 or greater than 0"
        }
    }

    val isUnlimited: Boolean get() = value == -1

    companion object {
        val single = Limit(1)
        val unlimited = Limit(-1)
    }
}

fun String.toLimit(): Limit = toIntOrNull()?.let(::Limit) ?: throw IllegalArgumentException("Limit must be -1 or a positive integer")

fun Int.toLimit(): Limit = Limit(this)
