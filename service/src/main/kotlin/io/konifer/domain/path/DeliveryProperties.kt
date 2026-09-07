package io.konifer.domain.path

import io.konifer.infrastructure.property.ConfigurationPropertyKeys
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.PathPropertyKeys.DeliveryPropertyKeys
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

@Serializable
data class DeliveryProperties(
    @SerialName(DeliveryPropertyKeys.STRATEGY)
    val strategy: DeliveryStrategy = DeliveryStrategy.default,
    @SerialName(DeliveryPropertyKeys.PRESIGNED)
    val preSigned: PreSignedProperties = PreSignedProperties.default,
    @SerialName(DeliveryPropertyKeys.TEMPLATE)
    val template: TemplateProperties = TemplateProperties.default,
) {
    init {
        if (strategy == DeliveryStrategy.PRESIGNED) {
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
            DeliveryProperties(
                strategy = DeliveryStrategy.default,
                preSigned = PreSignedProperties.default,
                template = TemplateProperties.default,
            )
    }
}

@Serializable
data class PreSignedProperties(
    @SerialName(DeliveryPropertyKeys.PreSignedPropertyKeys.TTL)
    val ttl: Duration = DEFAULT_TTL,
) {
    companion object Factory {
        val DEFAULT_TTL = 30.minutes
        val default = PreSignedProperties()
    }
}

@Serializable
data class TemplateProperties(
    @SerialName(DeliveryPropertyKeys.TemplatePropertyKeys.STRING)
    val string: String,
) {
    init {
        require(string.isNotBlank()) {
            "${ConfigurationPropertyKeys.PathPropertyKeys.DELIVERY}." +
                "${DeliveryPropertyKeys.TEMPLATE}.${DeliveryPropertyKeys.TemplatePropertyKeys.STRING} " +
                "must be populated"
        }

        require(DISALLOWED_SCHEMES.none { string.startsWith(it, ignoreCase = true) }) {
            "${ConfigurationPropertyKeys.PathPropertyKeys.DELIVERY}." +
                "${DeliveryPropertyKeys.TEMPLATE}.${DeliveryPropertyKeys.TemplatePropertyKeys.STRING} " +
                "cannot start with: $DISALLOWED_SCHEMES"
        }
    }

    companion object Factory {
        const val TEMPLATE_BUCKET = "{bucket}"
        const val TEMPLATE_KEY = "{key}"
        private val DISALLOWED_SCHEMES = setOf("javascript:", "vbscript:", "data:")

        const val DEFAULT_STRING = "http://localhost"
        val default =
            TemplateProperties(
                string = DEFAULT_STRING,
            )
    }
}
