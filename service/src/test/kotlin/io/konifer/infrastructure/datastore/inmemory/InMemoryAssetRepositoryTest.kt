package io.konifer.infrastructure.datastore.inmemory

import io.konifer.domain.ports.AssetRepository
import io.konifer.infrastructure.datastore.AssetRepositoryTest

class InMemoryAssetRepositoryTest : AssetRepositoryTest() {
    override fun createRepository(): AssetRepository = InMemoryAssetRepository()
}
