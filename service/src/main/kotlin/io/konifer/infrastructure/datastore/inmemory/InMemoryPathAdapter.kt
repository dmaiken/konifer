package io.konifer.infrastructure.datastore.inmemory

object InMemoryPathAdapter {
    fun toInMemoryPathFromUriPath(uriPath: String): String = "/${uriPath.removePrefix("/").removeSuffix("/")}"
}
