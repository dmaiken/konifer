package io

import io.ktor.server.routing.RoutingRequest
import io.path.PathModifierOption

const val APP_CACHE_STATUS = "App-Cache-Status"

/**
 * Gets the entryId of the request.
 *
 * @throws IllegalArgumentException if the entryId supplied is not a valid, non-negative [Long]
 */
fun getEntryId(request: RoutingRequest): Long? {
    val suppliedEntryId = request.queryParameters["entryId"]
    return if (suppliedEntryId != null) {
        return try {
            val entryId = suppliedEntryId.toLong()
            if (entryId < 0) {
                throw IllegalArgumentException("entryId must be greater than 0")
            }
            entryId
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException("entryId must be a valid number", e)
        }
    } else {
        null
    }
}

fun getPathModifierOption(request: RoutingRequest): PathModifierOption? {
    return request.queryParameters["mode"]?.let { option ->
        PathModifierOption.fromString(option)
    }
}

fun getAppStatusCacheHeader(cacheHit: Boolean): Pair<String, String> {
    val value =
        if (cacheHit) {
            "hit"
        } else {
            "miss"
        }

    return Pair(APP_CACHE_STATUS, value)
}
