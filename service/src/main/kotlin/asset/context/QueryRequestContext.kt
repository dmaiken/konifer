package io.asset.context

import image.model.RequestedImageAttributes
import io.path.configuration.PathConfiguration

data class QueryRequestContext(
    val path: String,
    val pathConfiguration: PathConfiguration,
    val modifiers: QueryModifiers,
    val requestedImageAttributes: RequestedImageAttributes?,
)
