package io.direkt.infrastructure.http.signature

import io.direkt.infrastructure.properties.ConfigurationPropertyKeys.URL_SIGNING
import io.direkt.infrastructure.properties.ConfigurationPropertyKeys.UrlSigningConfigurationProperties.ALGORITHM
import io.direkt.infrastructure.properties.ConfigurationPropertyKeys.UrlSigningConfigurationProperties.ENABLED
import io.direkt.infrastructure.properties.ConfigurationPropertyKeys.UrlSigningConfigurationProperties.SECRET_KEY
import io.direkt.infrastructure.tryGetConfig
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.config.tryGetString

fun Application.configureSignatureVerification() {
    val urlSigningEnabled =
        environment.config
            .tryGetConfig(URL_SIGNING)
            ?.tryGetString(ENABLED)
            ?.toBoolean() ?: false

    if (urlSigningEnabled) {
        val configuredAlgorithm =
            environment.config
                .tryGetConfig(URL_SIGNING)
                ?.tryGetString(ALGORITHM)
                ?.let {
                    HmacSigningAlgorithm.fromConfig(it)
                } ?: HmacSigningAlgorithm.HMAC_SHA256

        val configuredSecretKey =
            environment.config
                .tryGetConfig(URL_SIGNING)
                ?.tryGetString(SECRET_KEY)
                ?: throw IllegalArgumentException("URL signing secret key not found - one must be configured if enabled")

        install(HmacSignatureVerification) {
            algorithm = configuredAlgorithm
            secretKey = configuredSecretKey
        }
    }
}
