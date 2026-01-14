package io.direkt.service.context

import io.direkt.domain.path.PathConfiguration
import io.direkt.domain.variant.Transformation
import io.direkt.service.context.modifiers.QueryModifiers

data class QueryRequestContext(
    val path: String,
    val pathConfiguration: PathConfiguration,
    val modifiers: QueryModifiers,
    val transformation: Transformation?,
    val labels: Map<String, String>,
)
