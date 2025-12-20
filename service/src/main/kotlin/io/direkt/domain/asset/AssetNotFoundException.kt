package io.direkt.domain.asset

class AssetNotFoundException(
    override val cause: Throwable? = null,
    override val message: String,
) : Exception(message)
