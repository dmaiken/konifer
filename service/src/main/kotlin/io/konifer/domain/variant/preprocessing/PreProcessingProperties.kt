package io.konifer.domain.variant.preprocessing

import io.konifer.infrastructure.property.ConfigurationPropertyKeys.PathPropertyKeys.TransformPropertyKeys.PreProcessingPropertyKeys.ENABLED
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.PathPropertyKeys.TransformPropertyKeys.PreProcessingPropertyKeys.IMAGE
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PreProcessingProperties(
    @SerialName(ENABLED)
    val enabled: Boolean = false,
    @SerialName(IMAGE)
    val image: ImagePreProcessingProperties = ImagePreProcessingProperties.default,
) {
    companion object Factory {
        val default =
            PreProcessingProperties(
                enabled = false,
                image = ImagePreProcessingProperties.default,
            )
    }
}
