package io.direkt.asset.context

import io.path.configuration.PathConfiguration

data class StoreRequestContext(
    val path: String,
    val pathConfiguration: PathConfiguration,
)
