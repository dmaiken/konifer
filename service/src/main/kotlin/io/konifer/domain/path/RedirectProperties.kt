package io.konifer.domain.path

import io.konifer.infrastructure.property.ConfigurationPropertyKeys.PathPropertyKeys.ObjectStorePropertyKeys
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

@Serializable
data class RedirectProperties(
    @SerialName(ObjectStorePropertyKeys.RedirectPropertyKeys.STRATEGY)
    val strategy: RedirectStrategy = RedirectStrategy.default,
    @SerialName(ObjectStorePropertyKeys.RedirectPropertyKeys.PRESIGNED)
    val preSigned: PreSignedProperties = PreSignedProperties.default,
    @SerialName(ObjectStorePropertyKeys.RedirectPropertyKeys.TEMPLATE)
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
    }
}

@Serializable
data class PreSignedProperties(
    @SerialName(ObjectStorePropertyKeys.RedirectPropertyKeys.PreSignedPropertyKeys.TTL)
    val ttl: Duration = DEFAULT_TTL,
) {
    companion object Factory {
        val DEFAULT_TTL = 30.minutes
        val default = PreSignedProperties()
    }
}

@Serializable
data class TemplateProperties(
    @SerialName(ObjectStorePropertyKeys.RedirectPropertyKeys.TemplatePropertyKeys.STRING)
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
    }

    fun resolve(
        bucket: String,
        key: String,
    ): String = string.replace(TEMPLATE_BUCKET, bucket).replace(TEMPLATE_KEY, key)
}
