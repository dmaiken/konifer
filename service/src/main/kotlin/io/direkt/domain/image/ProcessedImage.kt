package io.direkt.domain.image

import io.direkt.domain.variant.Attributes
import io.direkt.domain.variant.LQIPs
import io.direkt.domain.variant.Transformation

data class ProcessedImage(
    val attributes: Attributes,
    val transformation: Transformation,
    val lqip: LQIPs,
)
