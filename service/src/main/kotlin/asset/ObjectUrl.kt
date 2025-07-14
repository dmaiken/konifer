package io.asset

data class ObjectUrl(
    val url: String,
    val cacheHit: Boolean,
) {
    fun getAppCacheStatusValue(): String {
        // Inspired from rfc9211 - Cache-Status header
        return if (cacheHit) {
            "hit"
        } else {
            "miss"
        }
    }
}
