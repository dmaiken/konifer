package io.konifer.domain.path

import io.konifer.infrastructure.property.ConfigurationPropertyKeys.PathPropertyKeys.ObjectStorePropertyKeys
import io.konifer.infrastructure.tryGetConfig
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.tryGetString
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

data class RedirectProperties(
    val strategy: RedirectStrategy = RedirectStrategy.default,
    val preSigned: PreSignedProperties = PreSignedProperties.default,
    val template: TemplateProperties = TemplateProperties.default,
) {
    init {
        if (strategy == RedirectStrategy.PRESIGNED) {
            require(preSigned.ttl.isPositive()) {
                "Presigned TTL must be positive"
            }
            require(preSigned.ttl <= 7.days) {
                "Presigned TTL cannot be greater than 7 days"
            }
        }
    }

    companion object Factory {
        val default =
            RedirectProperties(
                strategy = RedirectStrategy.default,
                preSigned = PreSignedProperties.default,
                template = TemplateProperties.default,
            )

        fun create(
            applicationConfig: ApplicationConfig?,
            parent: RedirectProperties?,
        ): RedirectProperties =
            RedirectProperties(
                strategy =
                    applicationConfig
                        ?.tryGetString(ObjectStorePropertyKeys.RedirectPropertyKeys.STRATEGY)
                        ?.let { RedirectStrategy.fromConfig(it) }
                        ?: parent?.strategy
                        ?: RedirectStrategy.default,
                preSigned =
                    PreSignedProperties.create(
                        applicationConfig =
                            applicationConfig?.tryGetConfig(
                                ObjectStorePropertyKeys.RedirectPropertyKeys.PRESIGNED,
                            ),
                        parent = parent?.preSigned,
                    ),
                template =
                    TemplateProperties.create(
                        applicationConfig =
                            applicationConfig?.tryGetConfig(
                                ObjectStorePropertyKeys.RedirectPropertyKeys.TEMPLATE,
                            ),
                        parent = parent?.template,
                    ),
            )
    }
}

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
                            ObjectStorePropertyKeys.RedirectPropertyKeys.PreSignedPropertyKeys.TTL,
                        )?.let { Duration.parse(it) }
                        ?: parent?.ttl
                        ?: DEFAULT_TTL,
            )
    }
}

data class TemplateProperties(
    val string: String,
) {
    init {
        require(string.isNotBlank()) {
            "Redirect template must be populated"
        }

        require(DISALLOWED_SCHEMES.none { string.startsWith(it) }) {
            "Redirect template cannot start with: $DISALLOWED_SCHEMES"
        }
    }

    companion object Factory {
        private const val TEMPLATE_BUCKET = "{bucket}"
        private const val TEMPLATE_KEY = "{key}"
        private val DISALLOWED_SCHEMES = setOf("javascript:", "vbscript:", "data:")

        const val DEFAULT_STRING = "http://localhost"
        val default =
            TemplateProperties(
                string = DEFAULT_STRING,
            )

        fun create(
            applicationConfig: ApplicationConfig?,
            parent: TemplateProperties?,
        ): TemplateProperties =
            TemplateProperties(
                string =
                    applicationConfig?.tryGetString(ObjectStorePropertyKeys.RedirectPropertyKeys.TemplatePropertyKeys.STRING)
                        ?: parent?.string
                        ?: DEFAULT_STRING,
            )
    }

    fun resolve(
        bucket: String,
        key: String,
    ): String = string.replace(TEMPLATE_BUCKET, bucket).replace(TEMPLATE_KEY, key)
}
