package io.direkt.service.context

import io.direkt.domain.path.PathConfiguration

data class StoreRequestContext(
    val path: String,
    val pathConfiguration: PathConfiguration,
)
