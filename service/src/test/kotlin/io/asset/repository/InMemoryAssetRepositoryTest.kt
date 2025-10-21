package io.asset.repository

class InMemoryAssetRepositoryTest : AssetRepositoryTest() {
    override fun createRepository(): AssetRepository {
        return InMemoryAssetRepository()
    }
}
