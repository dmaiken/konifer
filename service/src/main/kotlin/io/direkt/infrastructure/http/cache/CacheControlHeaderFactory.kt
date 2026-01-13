package io.direkt.infrastructure.http.cache

import io.direkt.domain.path.CacheControlProperties

object CacheControlHeaderFactory {
    /**
     * Construct a cache-control header value. This makes no assumption about the validity of the header.
     * It is up to the customer to configure the cache-control header correctly. All this guarantees is
     * the value will be correct syntactically.
     */
    fun constructHeader(properties: CacheControlProperties): String {
        val directives =
            listOfNotNull(
                properties.maxAge?.let { "max-age=$it" },
                properties.sharedMaxAge?.let { "s-maxage=$it" },
                properties.visibility?.value,
                properties.revalidate?.value,
                properties.staleWhileRevalidate?.let { "stale-while-revalidate=$it" },
                properties.staleIfError?.let { "stale-if-error=$it" },
                properties.immutable?.takeIf { it }?.let { "immutable" },
            )

        return directives.joinToString(", ")
    }
}
