package io.konifer.domain.ports

import io.konifer.domain.asset.AssetPath
import io.konifer.domain.path.PathConfiguration

interface PathConfigurationRepository {
    fun fetch(path: AssetPath): PathConfiguration
}
