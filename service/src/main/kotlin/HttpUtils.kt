package io

const val APP_CACHE_STATUS = "App-Cache-Status"

fun getAppStatusCacheHeader(cacheHit: Boolean): Pair<String, String> {
    val value =
        if (cacheHit) {
            "hit"
        } else {
            "miss"
        }

    return Pair(APP_CACHE_STATUS, value)
}
