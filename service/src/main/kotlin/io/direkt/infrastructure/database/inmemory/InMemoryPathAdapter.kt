package io.direkt.infrastructure.database.inmemory

object InMemoryPathAdapter {
    fun toInMemoryPathFromUriPath(uriPath: String): String = uriPath.removeSuffix("/")
}