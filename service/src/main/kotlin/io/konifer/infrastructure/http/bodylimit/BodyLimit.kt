package io.konifer.infrastructure.http.bodylimit

import io.konifer.domain.ByteSize
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

val DEFAULT_UPLOAD_BODY_LIMIT = ByteSize.parse("20MB")
val NON_MULTIPART_BODY_LIMIT = ByteSize.parse("1MB")

fun Application.configureRequestBodyLimit() {
    val multipartBodyLimit =
        environment.config
            .tryGetConfig(SOURCE)
            ?.tryGetConfig(MULTIPART)
            ?.tryGetString(MAX_BYTES)
            ?.let { ByteSize.parse(it) }
            ?: DEFAULT_UPLOAD_BODY_LIMIT

    install(RequestBodyLimit) {
        bodyLimit { call ->
            when (call.request.contentType().withoutParameters()) {
                ContentType.MultiPart.FormData -> multipartBodyLimit.bytes
                else -> NON_MULTIPART_BODY_LIMIT.bytes
            }
        }
    }
}
