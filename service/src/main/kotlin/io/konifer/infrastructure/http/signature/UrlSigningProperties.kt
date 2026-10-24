package io.konifer.infrastructure.http.signature

import io.konifer.infrastructure.EnvironmentVariable
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.URL_SIGNING
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.UrlSigningConfigurationPropertyKeys.ALGORITHM
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.UrlSigningConfigurationPropertyKeys.ENABLED
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.UrlSigningConfigurationPropertyKeys.SECRET_KEY
import io.konifer.infrastructure.tryGetConfig
import io.konifer.infrastructure.tryGetStringWithEnvironmentVariableOverride
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.tryGetString

class UrlSigningProperties(
    val enabled: Boolean = false,
    val secretKey: String = "",
    val algorithm: HmacSigningAlgorithm = HmacSigningAlgorithm.HMAC_SHA256,
    val signatureParam: String = "s",
) {
    val signer: HmacUrlSigner? =
        if (enabled) {
            HmacUrlSigner(
                SignatureConfig().also {
                    it.secretKey = secretKey
                    it.algorithm = algorithm
                    it.signatureParam = signatureParam
                },
            )
        } else {
            null
        }

    companion object {
        fun fromConfig(config: ApplicationConfig): UrlSigningProperties {
            val signing = config.tryGetConfig(URL_SIGNING)
            if (signing?.tryGetString(ENABLED)?.toBoolean() != true) return UrlSigningProperties()
            return UrlSigningProperties(
                enabled = true,
                secretKey =
                    signing.tryGetStringWithEnvironmentVariableOverride(SECRET_KEY, EnvironmentVariable.URL_SIGNING_SECRET_KEY)
                        ?: throw IllegalArgumentException("URL signing secret key not found - one must be configured if enabled"),
                algorithm = signing.tryGetString(ALGORITHM)?.let(HmacSigningAlgorithm::fromConfig) ?: HmacSigningAlgorithm.HMAC_SHA256,
            )
        }
    }
}
