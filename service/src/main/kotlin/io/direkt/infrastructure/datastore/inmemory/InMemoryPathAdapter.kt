package io.direkt.infrastructure.datastore.inmemory

object InMemoryPathAdapter {
    fun toInMemoryPathFromUriPath(uriPath: String): String = uriPath.removeSuffix("/")
}
