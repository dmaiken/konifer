package io.konifer.infrastructure.objectstore.property

import io.konifer.infrastructure.properties.ConfigurationPropertyKeys
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.tryGetString
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

data class PreSignedProperties(
    val ttl: Duration = DEFAULT_TTL,
) {
    companion object Factory {
        val DEFAULT_TTL = 30.minutes
        val default = PreSignedProperties()

        fun create(
            applicationConfig: ApplicationConfig?,
            parent: PreSignedProperties?,
        ): PreSignedProperties =
            PreSignedProperties(
                ttl =
                    applicationConfig
                        ?.tryGetString(
                            ConfigurationPropertyKeys.PathPropertyKeys.ObjectStorePropertyKeys.PreSignedProperties.TTL,
                        )?.let { Duration.parse(it) }
                        ?: parent?.ttl
                        ?: DEFAULT_TTL,
            )
    }
}

data class CdnProperties(
    val domain: String? = DEFAULT_DOMAIN,
) {
    companion object Factory {
        val default = CdnProperties()
        val DEFAULT_DOMAIN = null

        fun create(
            applicationConfig: ApplicationConfig?,
            parent: CdnProperties?,
        ): CdnProperties =
            CdnProperties(
                domain =
                    applicationConfig?.tryGetString(ConfigurationPropertyKeys.PathPropertyKeys.ObjectStorePropertyKeys.CdnProperties.DOMAIN)
                        ?: parent?.domain
                        ?: DEFAULT_DOMAIN,
            )
    }
}
