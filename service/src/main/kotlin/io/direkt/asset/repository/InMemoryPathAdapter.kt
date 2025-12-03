package io.direkt.asset.repository

object InMemoryPathAdapter {
    fun toInMemoryPathFromUriPath(uriPath: String): String = uriPath.removeSuffix("/")
}
