package io.direkt.service.context

import io.direkt.domain.image.Transformation
import io.direkt.domain.path.PathConfiguration

data class QueryRequestContext(
    val path: String,
    val pathConfiguration: PathConfiguration,
    val modifiers: QueryModifiers,
    val transformation: Transformation?,
    val labels: Map<String, String>,
)
