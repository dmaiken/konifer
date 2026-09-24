package io.konifer.infrastructure.datastore.inmemory

import io.konifer.common.image.ImageFormat
import io.konifer.domain.asset.Asset
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.ports.AssetRepository
import io.konifer.domain.ports.PersistObjectStoreRequest
import io.konifer.domain.transformation.Transformation
import io.konifer.domain.transformation.toDimension
import io.konifer.domain.variant.Variant
import io.konifer.domain.variant.retention.CacheProperties
import io.konifer.infrastructure.datastore.AssetRepositoryTest
import io.konifer.infrastructure.datastore.createPendingAsset
import io.konifer.infrastructure.datastore.createPendingVariant
import io.konifer.infrastructure.objectstore.inmemory.InMemoryObjectStore
import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe
import io.ktor.utils.io.ByteChannel
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneOffset.UTC

class InMemoryAssetRepositoryTest : AssetRepositoryTest() {
    companion object {
        private val inMemoryObjectStore by lazy { InMemoryObjectStore() }
    }

    override fun createRepository(): AssetRepository = InMemoryAssetRepository(inMemoryObjectStore)

    @BeforeEach
    fun clearObjectStore() {
        inMemoryObjectStore.clearObjectStore()
    }

    @Nested
    inner class VariantEvictionTests {
        @Test
        fun `when variant is evicted from cache then object is also deleted from object store`() =
            runTest {
                val pending = createPendingAsset()
                persistObject(pending.variants.single())
                val persisted = repository.storeNew(pending)
                repository.markReady(persisted.markReady(uploadedAt = LocalDateTime.now(UTC)))

                val previouslyReadyVariants =
                    (1..3).map { index ->
                        createPersistedAndUploadVariant(
                            asset = persisted,
                            transformation =
                                Transformation(
                                    format = ImageFormat.HEIC,
                                    height = (500 + index).toDimension(),
                                    width = (500 + index).toDimension(),
                                    colorSpace = ColorSpace.SRGB,
                                ),
                            maxVariants = 3,
                        )
                    }

                val newlyUploadedVariant =
                    createPersistedAndUploadVariant(
                        asset = persisted,
                        transformation =
                            Transformation(
                                format = ImageFormat.HEIC,
                                height = 700.toDimension(),
                                width = 700.toDimension(),
                                colorSpace = ColorSpace.SRGB,
                            ),
                        maxVariants = 1,
                    )

                previouslyReadyVariants.forAll {
                    inMemoryObjectStore.exists(
                        bucket = it.objectStoreBucket,
                        key = it.objectStoreKey,
                    ) shouldBe false
                }
                inMemoryObjectStore.exists(
                    bucket = newlyUploadedVariant.objectStoreBucket,
                    key = newlyUploadedVariant.objectStoreKey,
                ) shouldBe true
                inMemoryObjectStore.exists(
                    bucket = persisted.variants.single().objectStoreBucket,
                    key = persisted.variants.single().objectStoreKey,
                ) shouldBe true
            }
    }

    private suspend fun createPersistedAndUploadVariant(
        asset: Asset,
        transformation: Transformation,
        maxVariants: Int,
    ): Variant.Pending {
        val variant =
            repository.storeNewVariant(
                createPendingVariant(
                    assetId = asset.id,
                    transformation = transformation,
                ),
            )
        persistObject(variant)
        repository.markUploaded(
            variant = variant.markReady(LocalDateTime.now(UTC)),
            cacheProperties = CacheProperties(maxVariants = maxVariants),
        )
        return variant
    }

    private suspend fun persistObject(variant: Variant) {
        inMemoryObjectStore.persist(
            request =
                PersistObjectStoreRequest(
                    bucket = variant.objectStoreBucket,
                    key = variant.objectStoreKey,
                    contentType = variant.attributes.format,
                ),
            channel = ByteChannel().apply { close() },
        )
    }
}
