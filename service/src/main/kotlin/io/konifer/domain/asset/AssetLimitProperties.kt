package io.konifer.domain.asset

import io.konifer.domain.transformation.Dimension
import io.konifer.domain.transformation.toDimension
import io.konifer.domain.variant.PixelCount
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.PathPropertyKeys.AssetLimitPropertyKeys.MAX_HEIGHT
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.PathPropertyKeys.AssetLimitPropertyKeys.MAX_PAGES
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.PathPropertyKeys.AssetLimitPropertyKeys.MAX_PIXELS
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.PathPropertyKeys.AssetLimitPropertyKeys.MAX_PIXELS_PER_PAGE
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.PathPropertyKeys.AssetLimitPropertyKeys.MAX_WIDTH
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AssetLimitProperties(
    @SerialName(MAX_WIDTH)
    val maxWidth: Dimension = 8192.toDimension(),
    @SerialName(MAX_HEIGHT)
    val maxHeight: Dimension = 8192.toDimension(),
    @SerialName(MAX_PIXELS)
    val maxPixels: PixelCount = PixelCount.parse("20MP"),
    @SerialName(MAX_PAGES)
    val maxPages: Dimension = 20.toDimension(),
    @SerialName(MAX_PIXELS_PER_PAGE)
    val maxPixelsPerPage: PixelCount = PixelCount.parse("1MP"),
) {
    companion object Factory {
        val default: AssetLimitProperties = AssetLimitProperties()
    }
}
