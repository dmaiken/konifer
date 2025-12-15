package io.direkt.domain.image

import io.direkt.domain.variant.Attributes
import io.direkt.domain.variant.LQIPs
import java.io.File

data class PreProcessedImage(
    val attributes: Attributes,
    val lqip: LQIPs,
)
