package io.konifer.infrastructure.http.bodylimit

import io.konifer.infrastructure.property.ConfigurationPropertyKeys.SOURCE
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.SourceConfigurationPropertyKeys.MULTIPART
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.SourceConfigurationPropertyKeys.MultipartConfigurationPropertyKeys.MAX_BYTES
import io.konifer.infrastructure.tryGetConfig
import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.config.tryGetString
import io.ktor.server.plugins.bodylimit.RequestBodyLimit
import io.ktor.server.request.contentType

const val MEGABYTE = 1024L * 1024

const val DEFAULT_UPLOAD_BODY_LIMIT = 20 * MEGABYTE
const val NON_MULTIPART_BODY_LIMIT = 1L * MEGABYTE

fun Application.configureRequestBodyLimit() {
    val multipartBodyLimit =
        environment.config
            .tryGetConfig(SOURCE)
            ?.tryGetConfig(MULTIPART)
            ?.tryGetString(MAX_BYTES)
            ?.toLong()
            ?: DEFAULT_UPLOAD_BODY_LIMIT

    install(RequestBodyLimit) {
        bodyLimit { call ->
            when (call.request.contentType().withoutParameters()) {
                ContentType.MultiPart.FormData -> multipartBodyLimit
                else -> NON_MULTIPART_BODY_LIMIT
            }
        }
    }
}
