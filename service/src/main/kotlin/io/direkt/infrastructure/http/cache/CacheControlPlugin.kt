package io.direkt.infrastructure.http.cache

import io.direkt.domain.path.CacheControlProperties
import io.direkt.infrastructure.http.CustomAttributes.queryRequestContextKey
import io.direkt.infrastructure.http.RequestContextPlugin
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.request.httpMethod

/**
 * Sets the Cache-Control header on responses. Depends on [RequestContextPlugin] being installed.
 */
val AssetCacheControlPlugin = createRouteScopedPlugin(
    name = "AssetCacheControl",
) {
    onCallRespond { call ->
        when (call.request.httpMethod) {
            HttpMethod.Get -> {
                val requestContext = call.attributes[queryRequestContextKey]
                val properties = requestContext.pathConfiguration.cacheControl
                if (properties.enabled) {
                    call.response.headers.append(
                        name = HttpHeaders.CacheControl,
                        value = CacheControlHeaderFactory.constructHeader(properties),
                    )
                    call.response.headers.append(
                        name = HttpHeaders.Vary,
                        value = "Accept"
                    )
                }
            }
        }
    }
}

object CacheControlHeaderFactory {

    /**
     * Construct a cache-control header value. This makes no assumption about the validity of the header.
     * It is up to the customer to configure the cache-control header correctly. All this guarantees is
     * the value will be correct syntactically.
     */
    fun constructHeader(properties: CacheControlProperties): String {
        val directives = listOfNotNull(
            properties.maxAge?.let { "max-age=$it" },
            properties.sharedMaxAge?.let { "shared-max-age=$it" },
            properties.visibility?.value,
            properties.revalidate?.value,
            properties.staleWhileRevalidate?.let { "stale-while-revalidate=$it" },
            properties.staleIfError?.let { "stale-if-error=$it" },
            properties.immutable?.takeIf { it }?.let { "immutable" }
        )

        return directives.joinToString(", ")
    }
}