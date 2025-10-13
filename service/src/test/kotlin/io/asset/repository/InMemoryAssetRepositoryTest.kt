package io.asset.repository

import io.asset.variant.VariantParameterGenerator

class InMemoryAssetRepositoryTest : AssetRepositoryTest() {
    override fun createRepository(): AssetRepository {
        return InMemoryAssetRepository(VariantParameterGenerator())
    }
}
