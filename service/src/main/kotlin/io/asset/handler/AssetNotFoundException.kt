package io.asset.handler

class AssetNotFoundException(override val cause: Throwable?, override val message: String) : Exception(message)
