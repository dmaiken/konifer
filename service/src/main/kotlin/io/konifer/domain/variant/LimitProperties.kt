package io.konifer.domain.variant

import io.konifer.domain.transformation.Dimension
import io.konifer.domain.transformation.toDimension
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.PathPropertyKeys.TransformPropertyKeys.LimitsPropertyKeys.MAX_HEIGHT
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.PathPropertyKeys.TransformPropertyKeys.LimitsPropertyKeys.MAX_PIXELS
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.PathPropertyKeys.TransformPropertyKeys.LimitsPropertyKeys.MAX_WIDTH
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LimitProperties(
    @SerialName(MAX_WIDTH)
    val maxWidth: Dimension = 8192.toDimension(),
    @SerialName(MAX_HEIGHT)
    val maxHeight: Dimension = 8192.toDimension(),
    @SerialName(MAX_PIXELS)
    val maxPixels: PixelCount = (8192L * 8192L).toPixelCount(),
) {
    companion object Factory {
        val default = LimitProperties()
    }
}
