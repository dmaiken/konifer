package io.konifer.infrastructure.datastore.inmemory

object InMemoryPathAdapter {
    fun toInMemoryPathFromUriPath(uriPath: String): String =
        if (uriPath.startsWith("/")) {
            uriPath.removeSuffix("/")
        } else {
            "/${uriPath.removeSuffix("/")}"
        }
}
