package io.direkt.service.context

import io.direkt.image.model.Transformation
import io.direkt.path.configuration.PathConfiguration

data class QueryRequestContext(
    val path: String,
    val pathConfiguration: PathConfiguration,
    val modifiers: QueryModifiers,
    val transformation: Transformation?,
    val labels: Map<String, String>,
)
