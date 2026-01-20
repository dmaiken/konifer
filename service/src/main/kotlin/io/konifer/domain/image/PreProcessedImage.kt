package io.konifer.domain.image

import io.konifer.domain.variant.Attributes
import io.konifer.domain.variant.LQIPs

data class PreProcessedImage(
    val attributes: Attributes,
    val lqip: LQIPs,
)
