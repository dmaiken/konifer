package io.konifer.domain.image

import io.konifer.domain.variant.Attributes
import io.konifer.domain.variant.LQIPs
import io.konifer.domain.variant.Transformation

data class ProcessedImage(
    val attributes: Attributes,
    val transformation: Transformation,
    val lqip: LQIPs,
)
