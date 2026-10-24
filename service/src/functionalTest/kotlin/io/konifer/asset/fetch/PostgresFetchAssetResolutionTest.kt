package io.konifer.asset.fetch

import io.konifer.BaseFunctionalTest
import io.konifer.ImageFactory.testImage
import io.konifer.common.http.StoreAssetRequest
import io.konifer.domain.ports.AssetRepository
import io.konifer.infrastructure.datastore.postgres.PostgresAssetRepository
import io.konifer.infrastructure.datastore.postgres.postgresContainer
import io.konifer.infrastructure.http.APP_CACHE_STATUS
import io.konifer.testPostgres
import io.konifer.util.storeAssetMultipartSource
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
class PostgresFetchAssetResolutionTest : BaseFunctionalTest() {
    companion object {
        @JvmStatic
        @Container
        private val postgres = postgresContainer()
    }

    @Test
    fun `generated response uses persisted metadata without reloading asset`() {
        lateinit var repository: ObservedAssetRepository
        lateinit var persistedRepository: PostgresAssetRepository
        testPostgres(
            postgres,
            modules =
                listOf(
                    module {
                        single<AssetRepository> {
                            persistedRepository = get<PostgresAssetRepository>()
                            ObservedAssetRepository(persistedRepository).also { repository = it }
                        }
                    },
                ),
        ) {
            storeAssetMultipartSource(client, testImage().bytes, StoreAssetRequest(), path = "resolution")
            repository.resetReads()

            val generated = client.get("/assets/resolution/-/content?width=100")
            generated.status shouldBe HttpStatusCode.OK
            generated.headers[APP_CACHE_STATUS] shouldBe "miss"
            val generatedContent = generated.bodyAsBytes()
            repository.assetForDeliveryReads.get() shouldBe 1
            repository.assetReads.get() shouldBe 0
            repository.collectionReads.get() shouldBe 0
            repository.variantReads.get() shouldBe 0
            val uploaded = repository.uploadedVariant!!
            val persisted = persistedRepository.fetchVariantForDelivery(uploaded.assetId, uploaded.specification)!!
            persisted.id shouldBe uploaded.id
            persisted.attributes shouldBe uploaded.attributes
            persisted.objectStoreKey shouldBe uploaded.objectStoreKey
            val uploadedMetadata = checkNotNull(repository.uploadedMetadata)
            persisted.id shouldBe uploadedMetadata.id
            persisted.attributes shouldBe uploadedMetadata.attributes
            persisted.specification shouldBe uploadedMetadata.specification
            persisted.isOriginalVariant shouldBe uploadedMetadata.isOriginalVariant
            persisted.objectStoreBucket shouldBe uploadedMetadata.objectStoreBucket
            persisted.objectStoreKey shouldBe uploadedMetadata.objectStoreKey
            persisted.lqips shouldBe uploadedMetadata.lqips
            persisted.lqips shouldBe uploaded.lqips

            repository.resetReads()
            val cached = client.get("/assets/resolution/-/content?width=100")
            cached.status shouldBe HttpStatusCode.OK
            cached.headers[APP_CACHE_STATUS] shouldBe "hit"
            cached.bodyAsBytes().contentEquals(generatedContent) shouldBe true
            repository.assetForDeliveryReads.get() shouldBe 1
            repository.assetReads.get() shouldBe 0
            repository.collectionReads.get() shouldBe 0
            repository.variantReads.get() shouldBe 0
        }
    }
}
