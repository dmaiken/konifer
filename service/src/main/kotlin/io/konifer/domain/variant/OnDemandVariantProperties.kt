package io.konifer.domain.variant

import io.konifer.common.serializer.LowercaseEnumSerializer
import io.konifer.infrastructure.property.ConfigurationPropertyKeys
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OnDemandVariantProperties(
    @SerialName(ConfigurationPropertyKeys.PathPropertyKeys.TransformPropertyKeys.OnDemandVariantsPropertyKeys.MODE)
    val mode: OnDemandVariantMode = OnDemandVariantMode.default,
) {
    companion object Factory {
        val default = OnDemandVariantProperties()
    }
}

@Serializable(with = OnDemandVariantModeSerializer::class)
enum class OnDemandVariantMode {
    ENABLED,
    PROFILE_ONLY,
    ;

    companion object Factory {
        val default = ENABLED
    }
}

class OnDemandVariantModeSerializer : KSerializer<OnDemandVariantMode> by LowercaseEnumSerializer(OnDemandVariantMode.entries)
