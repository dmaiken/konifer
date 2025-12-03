package io.direkt.asset.repository

class InMemoryAssetRepositoryTest : AssetRepositoryTest() {
    override fun createRepository(): AssetRepository = InMemoryAssetRepository()
}
