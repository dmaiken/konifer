package io.direkt.infrastructure.inmemory

object InMemoryPathAdapter {
    fun toInMemoryPathFromUriPath(uriPath: String): String = uriPath.removeSuffix("/")
}