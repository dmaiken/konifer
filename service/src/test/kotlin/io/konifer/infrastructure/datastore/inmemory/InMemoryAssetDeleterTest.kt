package io.konifer.infrastructure.datastore.inmemory

import io.konifer.common.image.ImageFormat
import io.konifer.common.selector.Order
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.ports.DeleteAssetsCommand
import io.konifer.domain.ports.ObjectStore
import io.konifer.domain.transformation.Transformation
import io.konifer.domain.transformation.toDimension
import io.konifer.infrastructure.datastore.createPendingAsset
import io.konifer.infrastructure.datastore.createPendingVariant
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneOffset.UTC

class InMemoryAssetDeleterTest {
    private val objectStore = mockk<ObjectStore>(relaxed = true)
    private val assetRepository = InMemoryAssetRepository()
    private val assetDeleter = InMemoryAssetDeleter(objectStore, assetRepository)

    @Test
    fun `deletes every object belonging to an entry`() =
        runTest {
            val originalKey = "original.png"
            val variantKey = "expired-variant.webp"
            val asset =
                assetRepository
                    .storeNew(
                        createPendingAsset(
                            objectStoreBucket = "originals",
                            objectStoreKey = originalKey,
                        ),
                    ).markReady(LocalDateTime.now(UTC))
                    .also { assetRepository.markReady(it) }
            assetRepository.storeNewVariant(
                createPendingVariant(
                    assetId = asset.id,
                    objectStoreBucket = "variants",
                    objectStoreKey = variantKey,
                    transformation =
                        Transformation(
                            width = 50.toDimension(),
                            height = 50.toDimension(),
                            format = ImageFormat.WEBP,
                            colorSpace = ColorSpace.SRGB,
                        ),
                    expiresAt = LocalDateTime.now(UTC).minusDays(1),
                ),
            )

            assetDeleter.delete(
                DeleteAssetsCommand.Entry(
                    path = asset.path,
                    entryId = checkNotNull(asset.entryId),
                ),
            )

            coVerify(exactly = 1) { objectStore.deleteAll("originals", listOf(originalKey)) }
            coVerify(exactly = 1) { objectStore.deleteAll("variants", listOf(variantKey)) }
            assetRepository.fetchByPath(
                path = asset.path,
                entryId = asset.entryId,
                transformation = null,
                includeOnlyReady = false,
            ) shouldBe null
        }

    @Test
    fun `deletes objects belonging to pending assets at a path`() =
        runTest {
            val key = "pending.png"
            val asset =
                assetRepository.storeNew(
                    createPendingAsset(
                        objectStoreBucket = "bucket",
                        objectStoreKey = key,
                    ),
                )

            assetDeleter.delete(
                DeleteAssetsCommand.AtPath(
                    path = asset.path,
                    labels = emptyMap(),
                    order = Order.NEW,
                    limit = -1,
                ),
            )

            coVerify(exactly = 1) { objectStore.deleteAll("bucket", listOf(key)) }
            assetRepository.fetchByPath(
                path = asset.path,
                entryId = asset.entryId,
                transformation = null,
                includeOnlyReady = false,
            ) shouldBe null
        }

    @Test
    fun `recursive deletion only cleans objects for matching labels`() =
        runTest {
            val deleted =
                assetRepository.storeNew(
                    createPendingAsset(
                        path = "/users/123/profile",
                        labels = mapOf("animal" to "cat"),
                        objectStoreKey = "cat.png",
                    ),
                )
            val retained =
                assetRepository.storeNew(
                    createPendingAsset(
                        path = "/users/123/avatar",
                        labels = mapOf("animal" to "dog"),
                        objectStoreKey = "dog.png",
                    ),
                )

            assetDeleter.delete(
                DeleteAssetsCommand.Recursively(
                    path = "/users/123",
                    labels = mapOf("animal" to "cat"),
                ),
            )

            coVerify(exactly = 1) { objectStore.deleteAll("bucket", listOf("cat.png")) }
            assetRepository.fetchByPath(
                path = deleted.path,
                entryId = deleted.entryId,
                transformation = null,
                includeOnlyReady = false,
            ) shouldBe null
            assetRepository
                .fetchByPath(
                    path = retained.path,
                    entryId = retained.entryId,
                    transformation = null,
                    includeOnlyReady = false,
                )?.id shouldBe retained.id
        }
}
