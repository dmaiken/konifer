package io.konifer.infrastructure.http.cache

import io.konifer.domain.path.CacheControlProperties
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall

fun ApplicationCall.appendAssetCacheControlHeaders(properties: CacheControlProperties) {
    if (properties.enabled) {
        response.headers.append(HttpHeaders.CacheControl, CacheControlHeaderFactory.constructHeader(properties))
        response.headers.append(HttpHeaders.Vary, "Accept")
    }
}
