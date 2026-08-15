package io.konifer.domain.variant

import io.konifer.infrastructure.property.ConfigurationPropertyKeys.PathPropertyKeys.TransformPropertyKeys.LimitsPropertyKeys.MAX_HEIGHT
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.PathPropertyKeys.TransformPropertyKeys.LimitsPropertyKeys.MAX_PIXELS
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.PathPropertyKeys.TransformPropertyKeys.LimitsPropertyKeys.MAX_WIDTH
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LimitProperties(
    @SerialName(MAX_WIDTH)
    val maxWidth: Int = 8192,
    @SerialName(MAX_HEIGHT)
    val maxHeight: Int = 8192,
    @SerialName(MAX_PIXELS)
    val maxPixels: Long = 8192 * 8192,
) {
    companion object Factory {
        val default = LimitProperties()
    }
}
