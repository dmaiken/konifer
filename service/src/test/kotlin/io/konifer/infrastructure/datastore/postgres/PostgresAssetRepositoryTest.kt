package io.konifer.infrastructure.datastore.postgres

import io.konifer.common.image.ImageFormat
import io.konifer.common.image.pixel.PixelDataType
import io.konifer.common.image.pixel.PixelLayout
import io.konifer.common.selector.Order
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.ports.AssetRepository
import io.konifer.domain.transformation.Transformation
import io.konifer.domain.transformation.pixel.PixelTransformation
import io.konifer.domain.transformation.pixel.toPixelChannels
import io.konifer.domain.transformation.toDimension
import io.konifer.domain.variant.VariantSelection
import io.konifer.domain.variant.VariantSpecification
import io.konifer.domain.variant.attribute.Attributes
import io.konifer.domain.variant.attribute.PixelAttributes
import io.konifer.domain.variant.retention.CacheProperties
import io.konifer.infrastructure.datastore.AssetRepositoryTest
import io.konifer.infrastructure.datastore.createPendingAsset
import io.konifer.infrastructure.datastore.createPendingVariant
import io.konifer.infrastructure.datastore.postgres.scheduling.VariantDeletedEvent
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import konifer.jooq.tables.references.ASSET_LABEL
import konifer.jooq.tables.references.ASSET_TAG
import konifer.jooq.tables.references.ASSET_TREE
import konifer.jooq.tables.references.ASSET_VARIANT
import konifer.jooq.tables.references.OUTBOX
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.test.runTest
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDateTime
import java.time.ZoneOffset.UTC

@Testcontainers
class PostgresAssetRepositoryTest : AssetRepositoryTest() {
    companion object {
        @JvmStatic
        @Container
        private val postgres = postgresContainer()

        val dslContext: DSLContext by lazy { createR2dbcDslContext(postgres) }
    }

    @BeforeEach
    fun clearTables() {
        truncateTables(postgres)
    }

    override fun createRepository(): AssetRepository =
        PostgresAssetRepository(
            dslContext = dslContext,
        )

    @Test
    fun `stores and selects separate pixel layouts without uniqueness collisions`() =
        runTest {
            val persisted = repository.storeNew(createPendingAsset())
            repository.markReady(persisted.markReady(LocalDateTime.now(UTC)))
            val stored =
                PixelLayout.entries.associateWith { layout ->
                    val transformation =
                        Transformation(
                            width = 2.toDimension(),
                            height = 2.toDimension(),
                            format = ImageFormat.PIXELS,
                            colorSpace = ColorSpace.SRGB,
                            pixels = PixelTransformation("RGB".toPixelChannels(), layout),
                        )
                    val attributes =
                        Attributes(
                            width = transformation.width,
                            height = transformation.height,
                            format = ImageFormat.PIXELS,
                            colorSpace = ColorSpace.SRGB,
                            pixels = PixelAttributes("RGB".toPixelChannels(), layout, PixelDataType.UINT8),
                        )
                    val variant =
                        repository.storeNewVariant(
                            createPendingVariant(assetId = persisted.id, attributes = attributes, transformation = transformation),
                        )
                    repository.markUploaded(variant.markReady(LocalDateTime.now(UTC)), CacheProperties())
                    variant
                }

            repeat(2) {
                stored.forEach { (layout, variant) ->
                    val fetched =
                        repository.fetchByPath(persisted.path, persisted.entryId)!!.variants.single {
                            variant.specification.matches(it.specification) { it.attributes }
                        }

                    fetched.id shouldBe variant.id
                    fetched.attributes.pixels?.layout shouldBe layout
                    (fetched.specification as VariantSpecification.Transformed).transformation.pixels?.layout shouldBe layout
                }
            }
            stored.values.map { it.id }.toSet() shouldHaveSize 2
            repository.fetchByPath(persisted.path, persisted.entryId, VariantSelection.ALL)!!.variants shouldHaveSize 3
        }

    @Nested
    inner class DeleteCascadeTests {
        @Test
        fun `deleting an asset cascades to all tables`() =
            runTest {
                val ready =
                    repository
                        .storeNew(createPendingAsset())
                        .markReady(LocalDateTime.now(UTC))
                        .also { repository.markReady(it) }
                repository.deleteByPath(
                    path = "/users/123",
                    entryId = 0,
                )

                repository.fetchByPath(ready.path, ready.entryId, VariantSelection.ALL, Order.NEW) shouldBe null
                dslContext
                    .select()
                    .from(ASSET_TREE)
                    .where(ASSET_TREE.ID.eq(ready.id.value))
                    .awaitFirstOrNull() shouldBe null
                dslContext
                    .select()
                    .from(ASSET_VARIANT)
                    .where(ASSET_VARIANT.ASSET_ID.eq(ready.id.value))
                    .awaitFirstOrNull() shouldBe null
                dslContext
                    .select()
                    .from(ASSET_LABEL)
                    .where(ASSET_LABEL.ASSET_ID.eq(ready.id.value))
                    .awaitFirstOrNull() shouldBe null
                dslContext
                    .select()
                    .from(ASSET_TAG)
                    .where(ASSET_TAG.ASSET_ID.eq(ready.id.value))
                    .awaitFirstOrNull() shouldBe null
            }
    }

    @Nested
    inner class DeleteOutboxTests {
        @Test
        fun `deleteByPath schedules variants for reaping`() =
            runTest {
                val ready =
                    repository
                        .storeNew(createPendingAsset())
                        .markReady(LocalDateTime.now(UTC))
                        .also { repository.markReady(it) }
                repository.deleteByPath(
                    path = "/users/123",
                    entryId = 0,
                )
                val variant = ready.variants.first()

                dslContext
                    .select()
                    .from(OUTBOX)
                    .where(OUTBOX.EVENT_TYPE.eq(VariantDeletedEvent.TYPE))
                    .and(DSL.jsonbGetAttributeAsText(OUTBOX.PAYLOAD, "objectStoreBucket").eq(variant.objectStoreBucket))
                    .and(DSL.jsonbGetAttributeAsText(OUTBOX.PAYLOAD, "objectStoreKey").eq(variant.objectStoreKey))
                    .awaitFirstOrNull() shouldNotBe null
            }

        @Test
        fun `deleteAllByPath schedules variants for reaping`() =
            runTest {
                val ready =
                    repository
                        .storeNew(createPendingAsset())
                        .markReady(LocalDateTime.now(UTC))
                        .also { repository.markReady(it) }
                repository.deleteAllByPath(
                    path = "/users/123",
                    limit = -1,
                )
                val variant = ready.variants.first()

                dslContext
                    .select()
                    .from(OUTBOX)
                    .where(OUTBOX.EVENT_TYPE.eq(VariantDeletedEvent.TYPE))
                    .and(DSL.jsonbGetAttributeAsText(OUTBOX.PAYLOAD, "objectStoreBucket").eq(variant.objectStoreBucket))
                    .and(DSL.jsonbGetAttributeAsText(OUTBOX.PAYLOAD, "objectStoreKey").eq(variant.objectStoreKey))
                    .awaitFirstOrNull() shouldNotBe null
            }

        @Test
        fun `deleteRecursivelyByPath schedules variants for reaping`() =
            runTest {
                val ready =
                    repository
                        .storeNew(createPendingAsset())
                        .markReady(LocalDateTime.now(UTC))
                        .also { repository.markReady(it) }
                repository.deleteRecursivelyByPath(
                    path = "/users/123",
                )
                val variant = ready.variants.first()

                dslContext
                    .select()
                    .from(OUTBOX)
                    .where(OUTBOX.EVENT_TYPE.eq(VariantDeletedEvent.TYPE))
                    .and(DSL.jsonbGetAttributeAsText(OUTBOX.PAYLOAD, "objectStoreBucket").eq(variant.objectStoreBucket))
                    .and(DSL.jsonbGetAttributeAsText(OUTBOX.PAYLOAD, "objectStoreKey").eq(variant.objectStoreKey))
                    .awaitFirstOrNull() shouldNotBe null
            }
    }
}
