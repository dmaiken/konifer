package io.konifer.domain.ports

import io.konifer.domain.asset.AssetDataContainer

interface AssetContainerFactory {
    suspend fun fromUrlSource(urlSource: String?): AssetDataContainer
}
