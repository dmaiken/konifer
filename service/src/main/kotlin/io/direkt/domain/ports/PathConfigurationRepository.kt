package io.direkt.domain.ports

import io.direkt.domain.path.PathConfiguration

interface PathConfigurationRepository {

    fun fetch(path: String): PathConfiguration
}