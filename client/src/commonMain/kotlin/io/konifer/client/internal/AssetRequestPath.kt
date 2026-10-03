package io.konifer.client.internal

import io.konifer.common.selector.Order
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments

internal fun URLBuilder.appendAssetPath(path: String) {
    appendPathSegments("assets")
    appendPathSegments(path.removePrefix("/").removeSuffix("/").split("/"))
}

internal fun URLBuilder.appendPathSeparator() {
    appendPathSegments(PATH_SEPARATOR)
}

internal fun URLBuilder.appendEntrySelector(entryId: Long) {
    appendPathSegments("entry", entryId.toString())
}

internal fun URLBuilder.appendOrderSelector(order: Order) {
    appendPathSegments(order.name.lowercase())
}

internal fun URLBuilder.appendRecursiveSelector() {
    appendPathSegments("recursive")
}
