package io.direkt.service.context

import io.direkt.path.configuration.PathConfiguration

data class StoreRequestContext(
    val path: String,
    val pathConfiguration: PathConfiguration,
)
