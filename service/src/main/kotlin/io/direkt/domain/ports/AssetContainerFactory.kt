package io.direkt.domain.ports

import io.direkt.asset.AssetStreamContainer

interface AssetContainerFactory {

    suspend fun fromUrlSource(urlSource: String?): AssetStreamContainer
}