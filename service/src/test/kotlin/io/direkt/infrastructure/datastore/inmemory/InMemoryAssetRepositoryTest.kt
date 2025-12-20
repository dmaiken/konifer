package io.direkt.infrastructure.datastore.inmemory

import io.direkt.domain.ports.AssetRepository
import io.direkt.infrastructure.datastore.AssetRepositoryTest

class InMemoryAssetRepositoryTest : AssetRepositoryTest() {
    override fun createRepository(): AssetRepository = InMemoryAssetRepository()
}
