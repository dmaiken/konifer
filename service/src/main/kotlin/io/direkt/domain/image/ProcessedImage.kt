package io.direkt.domain.image

import io.direkt.domain.variant.Attributes
import io.direkt.domain.variant.LQIPs
import io.direkt.domain.variant.Transformation
import java.io.File

data class ProcessedImage(
    val attributes: Attributes,
    val transformation: Transformation,
    val lqip: LQIPs,
)
