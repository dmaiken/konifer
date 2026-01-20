package io.konifer.domain.ports

import io.konifer.domain.path.PathConfiguration

interface PathConfigurationRepository {
    fun fetch(path: String): PathConfiguration
}
