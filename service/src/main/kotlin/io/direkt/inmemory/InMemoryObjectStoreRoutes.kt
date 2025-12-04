package io.direkt.inmemory

import io.direkt.asset.store.InMemoryObjectStore
import io.direkt.asset.store.ObjectStore
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import org.koin.ktor.ext.inject

fun Application.configureInMemoryObjectStoreRouting() {
    val objectStore by inject<ObjectStore>()

    routing {
        get("objectStore/{bucket}/{key}") {
            val key = requireNotNull(call.parameters["key"])
            val bucket = requireNotNull(call.parameters["bucket"])

            if (objectStore.exists(bucket, key)) {
                call.respondBytesWriter(
                    contentType = ContentType.parse(call.parameters["contentType"] ?: "application/octet-stream"),
                    status = HttpStatusCode.OK,
                ) {
                    objectStore.fetch(bucket, key, this)
                }
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }

        delete("objectStore") {
            if (objectStore is InMemoryObjectStore) {
                (objectStore as InMemoryObjectStore).clearObjectStore()
            }
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
