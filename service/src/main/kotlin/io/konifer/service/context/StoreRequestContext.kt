package io.konifer.service.context

import io.konifer.domain.path.PathConfiguration

data class StoreRequestContext(
    val path: String,
    val pathConfiguration: PathConfiguration,
)
