package io.konifer.infrastructure.http.cache

import io.konifer.infrastructure.http.CustomAttributes.lastModifiedKey
import io.konifer.infrastructure.http.CustomAttributes.variantIdKey
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.content.EntityTagVersion
import io.ktor.http.content.LastModifiedVersion
import io.ktor.http.content.Version
import io.ktor.http.content.VersionCheckResult
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.conditionalheaders.ConditionalHeaders
import io.ktor.util.date.GMTDate

fun Application.configureConditionalHeaders() {
    install(ConditionalHeaders) {
        version { call, content ->
            listOfNotNull(
                call.attributes.getOrNull(variantIdKey)?.let { variantId ->
                    EntityTagVersion(
                        etag = "${variantId.value}:${call.attributes[lastModifiedKey]}",
                        // Raw pixels may be HTTP-compressed; their encoded bytes can vary.
                        weak = content.contentType?.withoutParameters() == ContentType.Application.OctetStream,
                    )
                },
                call.attributes.getOrNull(lastModifiedKey)?.let {
                    EntityTagAwareLastModifiedVersion(
                        LastModifiedVersion(GMTDate(it.epochSecond * 1000)),
                    )
                },
            )
        }
    }
}

private class EntityTagAwareLastModifiedVersion(
    private val delegate: LastModifiedVersion,
) : Version by delegate {
    override fun check(requestHeaders: Headers): VersionCheckResult {
        // Ktor checks each version independently. If-None-Match takes precedence over If-Modified-Since.
        val headers =
            if (requestHeaders.contains(HttpHeaders.IfNoneMatch)) {
                Headers.build {
                    appendAll(requestHeaders)
                    remove(HttpHeaders.IfModifiedSince)
                }
            } else {
                requestHeaders
            }
        return delegate.check(headers)
    }
}
