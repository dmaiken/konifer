package io.konifer.domain.asset

import io.konifer.domain.context.InvalidPathException

@JvmInline
value class AssetPath private constructor(
    val value: String,
) {
    init {
        if (value != "/" && (
                !value.startsWith("/") || value.endsWith("/") ||
                    !value.removePrefix("/").split('/').all { it.isNotEmpty() && it != "." && it != ".." && it.matches(VALID_SEGMENT) }
            )
        ) {
            throw InvalidPathException("Invalid path: $value")
        }
    }

    companion object {
        private val VALID_SEGMENT = Regex("[a-zA-Z0-9._~!$'()*+=@:-]+")
        val ROOT = AssetPath("/")

        operator fun invoke(value: String): AssetPath {
            if ("//" in value) throw InvalidPathException("Invalid path: $value")
            if (value.isEmpty() || value == "/") return ROOT
            val path = value.removePrefix("/").removeSuffix("/")
            return AssetPath("/$path")
        }
    }

    fun isSelfOrDescendantOf(parent: AssetPath): Boolean = parent.value == "/" || this == parent || value.startsWith(parent.value + "/")

    override fun toString(): String = value
}

fun String.toAssetPath(): AssetPath = AssetPath(this)
