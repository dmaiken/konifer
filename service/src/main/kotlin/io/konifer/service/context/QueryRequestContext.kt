package io.konifer.service.context

import io.konifer.domain.path.PathConfiguration
import io.konifer.domain.variant.Transformation
import io.konifer.service.context.modifiers.QueryModifiers

data class QueryRequestContext(
    val path: String,
    val pathConfiguration: PathConfiguration,
    val modifiers: QueryModifiers,
    val transformation: Transformation?,
    val labels: Map<String, String>,
)
