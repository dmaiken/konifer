package io.konifer.domain.variant

import io.konifer.domain.variant.preprocessing.PreProcessingProperties
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
    @SerialName(ConfigurationPropertyKeys.PathPropertyKeys.TransformPropertyKeys.EXPIRE)
    val expire: ExpirationProperties = ExpirationProperties.default,
    @SerialName(ConfigurationPropertyKeys.PathPropertyKeys.TransformPropertyKeys.LIMITS)
    val limits: LimitProperties = LimitProperties.default,
) {
    companion object Factory {
        val default = TransformProperties()
    }
}
