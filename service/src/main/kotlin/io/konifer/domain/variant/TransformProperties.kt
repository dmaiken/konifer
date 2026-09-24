package io.konifer.domain.variant

import io.konifer.domain.transformation.PreProcessingProperties
import io.konifer.domain.variant.retention.ExpirationProperties
import io.konifer.domain.variant.retention.RetentionProperties
import io.konifer.infrastructure.property.ConfigurationPropertyKeys
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TransformProperties(
    @SerialName(ConfigurationPropertyKeys.PathPropertyKeys.TransformPropertyKeys.PREPROCESSING)
    val preProcessing: PreProcessingProperties = PreProcessingProperties.default,
    @SerialName(ConfigurationPropertyKeys.PathPropertyKeys.TransformPropertyKeys.EAGER_VARIANTS)
    val eagerVariants: List<String> = emptyList(),
    @SerialName(ConfigurationPropertyKeys.PathPropertyKeys.TransformPropertyKeys.ON_DEMAND_VARIANT)
    val onDemandVariant: OnDemandVariantProperties = OnDemandVariantProperties.default,
    @SerialName(ConfigurationPropertyKeys.PathPropertyKeys.TransformPropertyKeys.RETENTION)
    val retention: RetentionProperties = RetentionProperties.default,
    @SerialName(ConfigurationPropertyKeys.PathPropertyKeys.TransformPropertyKeys.EXPIRE)
    val expire: ExpirationProperties = ExpirationProperties.default,
    @SerialName(ConfigurationPropertyKeys.PathPropertyKeys.TransformPropertyKeys.LIMITS)
    val limits: TransformationLimitProperties = TransformationLimitProperties.default,
) {
    init {
        require(eagerVariants.size < retention.cache.maxVariants) {
            "max-variants (${retention.cache.maxVariants}) cannot be less than number of " +
                "eager-variants (${eagerVariants.size}) defined for path"
        }
    }

    companion object Factory {
        val default = TransformProperties()
    }
}
