package io.konifer.infrastructure.http.cache

import io.konifer.infrastructure.http.CustomAttributes.queryRequestContextKey
import io.konifer.infrastructure.http.RequestContextPlugin
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.request.httpMethod

/**
 * Sets the Cache-Control header on responses. Depends on [RequestContextPlugin] being installed.
 */
val AssetCacheControlPlugin =
    createRouteScopedPlugin(
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
                            value = "Accept",
                        )
                    }
                }
            }
        }
    }
