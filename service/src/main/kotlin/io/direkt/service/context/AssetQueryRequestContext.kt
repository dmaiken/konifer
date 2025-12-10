package io.direkt.service.context

import io.direkt.domain.path.PathConfiguration
import io.direkt.domain.variant.Transformation

data class AssetQueryRequestContext(
    val path: String,
    val pathConfiguration: PathConfiguration,
    val modifiers: QueryModifiers,
    val transformation: Transformation?,
    val labels: Map<String, String>,
)

data class VariantQueryContext(
    val path: String,
    val pathConfiguration: PathConfiguration,
    val modifiers: QueryModifiers,
    val transformation: Transformation,
    val labels: Map<String, String>,
)
