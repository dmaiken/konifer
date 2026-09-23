package io.konifer.domain.variant.retention

import io.konifer.infrastructure.property.ConfigurationPropertyKeys.PathPropertyKeys.TransformPropertyKeys.RetentionPropertyKeys.CachePropertyKeys
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

@Serializable
data class CacheProperties(
    @SerialName(CachePropertyKeys.MAX_VARIANTS)
    val maxVariants: Int = 16,
    @SerialName(CachePropertyKeys.ACCESS_SCORE_HALF_LIFE)
    val accessScoreHalfLife: Duration = 1.hours,
) {
    init {
        require(maxVariants > 0) {
            "'${CachePropertyKeys.MAX_VARIANTS}' must be greater than zero"
        }
        require(accessScoreHalfLife.isPositive()) {
            "'${CachePropertyKeys.ACCESS_SCORE_HALF_LIFE}' must be greater than zero"
        }
    }
}
