package io.direkt.infrastructure.database.inmemory

import io.direkt.domain.ports.AssetRepository
import io.direkt.infrastructure.database.AssetRepositoryTest

class InMemoryAssetRepositoryTest : AssetRepositoryTest() {
    override fun createRepository(): AssetRepository = InMemoryAssetRepository()
}