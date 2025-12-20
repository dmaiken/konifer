package io.direkt.domain.image

import io.direkt.domain.variant.Attributes
import io.direkt.domain.variant.LQIPs

data class PreProcessedImage(
    val attributes: Attributes,
    val lqip: LQIPs,
)
