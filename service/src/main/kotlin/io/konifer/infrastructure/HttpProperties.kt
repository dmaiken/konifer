package io.konifer.infrastructure

import io.ktor.http.URLProtocol
import io.ktor.http.Url

data class HttpProperties(
    val publicUrl: Url? = null,
) {
    init {
        if (publicUrl != null) {
            require(publicUrl.protocolOrNull == URLProtocol.HTTP || publicUrl.protocolOrNull == URLProtocol.HTTPS) {
                "public-url must start with http:// or https://"
            }
        }
    }
}
