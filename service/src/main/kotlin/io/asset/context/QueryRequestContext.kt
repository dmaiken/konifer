package io.asset.context

import io.image.model.Transformation
import io.path.configuration.PathConfiguration

data class QueryRequestContext(
    val path: String,
    val pathConfiguration: PathConfiguration,
    val modifiers: QueryModifiers,
    val transformation: Transformation?,
    val labels: Map<String, String>,
)
