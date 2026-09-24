package io.konifer.infrastructure.datastore.inmemory

import io.konifer.common.image.ImageFormat
import io.konifer.common.selector.Order
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.ports.DeleteAssetsCommand
import io.konifer.domain.ports.PersistObjectStoreRequest
import io.konifer.domain.transformation.Transformation
import io.konifer.domain.transformation.toDimension
import io.konifer.infrastructure.datastore.createPendingAsset
import io.konifer.infrastructure.datastore.createPendingVariant
import io.konifer.infrastructure.objectstore.inmemory.InMemoryObjectStore
import io.kotest.matchers.shouldBe
import io.ktor.utils.io.ByteChannel
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneOffset.UTC

class InMemoryAssetDeleterTest {
    private val objectStore = InMemoryObjectStore()
    private val assetRepository = InMemoryAssetRepository(objectStore)
    private val assetDeleter = InMemoryAssetDeleter(objectStore, assetRepository)

    @Test
    fun `deletes every object belonging to an entry`() =
        runTest {
            val originalKey = "original.png"
            val variantKey = "expired-variant.webp"
            persistObject("originals", originalKey)
            persistObject("variants", variantKey, ImageFormat.WEBP)
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

            objectStore.exists("originals", originalKey) shouldBe false
            objectStore.exists("variants", variantKey) shouldBe false
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
            persistObject("bucket", key)
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

            objectStore.exists("bucket", key) shouldBe false
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
            persistObject("bucket", "cat.png")
            persistObject("bucket", "dog.png")
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

            objectStore.exists("bucket", "cat.png") shouldBe false
            objectStore.exists("bucket", "dog.png") shouldBe true
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

    private suspend fun persistObject(
        bucket: String,
        key: String,
        contentType: ImageFormat = ImageFormat.PNG,
    ) {
        objectStore.persist(
            request =
                PersistObjectStoreRequest(
                    bucket = bucket,
                    key = key,
                    contentType = contentType,
                ),
            channel = ByteChannel().apply { close() },
        )
    }
}
