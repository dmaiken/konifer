package io.direkt.infrastructure.database

import io.direkt.domain.ports.AssetRepository
import io.direkt.infrastructure.inmemory.InMemoryAssetRepository

class InMemoryAssetRepositoryTest : AssetRepositoryTest() {
    override fun createRepository(): AssetRepository = InMemoryAssetRepository()
}
