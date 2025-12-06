package io.direkt.domain.ports

import io.direkt.asset.AssetDataContainer

interface AssetContainerFactory {

    suspend fun fromUrlSource(urlSource: String?): AssetDataContainer
}