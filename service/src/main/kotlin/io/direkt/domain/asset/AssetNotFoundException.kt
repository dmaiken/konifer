package io.direkt.domain.asset

class AssetNotFoundException(
    override val cause: Throwable?,
    override val message: String,
) : Exception(message)
