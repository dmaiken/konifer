package io.konifer.infrastructure.datastore.postgres

import io.konifer.common.image.ImageFormat
import io.konifer.common.image.pixel.PixelDataType
import io.konifer.common.image.pixel.PixelLayout
import io.konifer.common.selector.Order
import io.konifer.domain.asset.Asset
import io.konifer.domain.asset.AssetPath
import io.konifer.domain.asset.toEntryId
import io.konifer.domain.context.Limit
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.ports.AssetRepository
import io.konifer.domain.transformation.Transformation
import io.konifer.domain.transformation.pixel.PixelTransformation
import io.konifer.domain.transformation.pixel.toPixelChannels
import io.konifer.domain.transformation.toDimension
import io.konifer.domain.variant.LQIPs
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
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.impl.DSL
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant

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
            repository.markReady(persisted.markReady(Instant.now()))
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
                    repository.markUploaded(variant.markReady(Instant.now()), CacheProperties())
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

    @Test
    fun `unreadable transformations are excluded from reads and updates without changing stored rows`() =
        runTest {
            val asset = repository.storeNew(createPendingAsset()).markReady(Instant.now())
            repository.markReady(asset)
            val variants =
                (10..50 step 10).map { width ->
                    val transformation = Transformation.fromAttributes(asset.variants.single().attributes).copy(width = width.toDimension())
                    repository.storeNewVariant(createPendingVariant(assetId = asset.id, transformation = transformation)).also {
                        repository.markUploaded(it.markReady(Instant.now()), CacheProperties())
                    }
                }
            val unreadable = variants.filterIndexed { index, _ -> index % 2 == 0 }
            val storedJson =
                unreadable.associate { variant ->
                    val json =
                        JSONB.valueOf(
                            """{"width":${(variant.specification as VariantSpecification.Transformed).transformation.width.value},"height":80,"format":"png","futureTransformation":true}""",
                        )
                    dslContext
                        .update(ASSET_VARIANT)
                        .set(ASSET_VARIANT.TRANSFORMATION, json)
                        .where(ASSET_VARIANT.ID.eq(variant.id.value))
                        .awaitSingle()
                    variant.id to json
                }
            val expectedIds = variants.filterNot { it in unreadable }.asReversed().map { it.id } + asset.variants.single().id

            repository.fetchByPath(asset.path, asset.entryId)!!.variants.map { it.id } shouldBe expectedIds
            val entries = repository.fetchAllByPath(asset.path, limit = Limit.single)
            entries.single().id shouldBe asset.id
            entries.single().variants.map { it.id } shouldBe expectedIds
            repository.fetchForDelivery(asset.path, asset.entryId)!!.variants.map { it.id } shouldBe expectedIds
            repository.fetchVariantsForDelivery(asset.id).map { it.id } shouldBe expectedIds
            val loaded = repository.fetchForUpdate(asset.path, asset.entryId!!) as Asset.Ready
            loaded.variants.map { it.id } shouldBe expectedIds
            repository.update(loaded.update(alt = "updated", labels = emptyMap(), tags = emptySet()))

            storedJson.forEach { (id, json) ->
                dslContext
                    .selectFrom(ASSET_VARIANT)
                    .where(ASSET_VARIANT.ID.eq(id.value))
                    .awaitSingle()
                    .transformation shouldBe json
            }

            variants.filterNot { it in unreadable }.forEach { variant ->
                dslContext
                    .update(ASSET_VARIANT)
                    .set(
                        ASSET_VARIANT.TRANSFORMATION,
                        JSONB.valueOf(
                            """{"width":${(variant.specification as VariantSpecification.Transformed).transformation.width.value},"height":80,"format":"png","futureTransformation":true}""",
                        ),
                    ).where(ASSET_VARIANT.ID.eq(variant.id.value))
                    .awaitSingle()
            }
            repository
                .fetchByPath(asset.path, asset.entryId)!!
                .variants
                .single()
                .isOriginalVariant shouldBe true
            repository.fetchAllByPath(asset.path, limit = Limit.single).single().id shouldBe asset.id
            repository
                .fetchAllByPath(asset.path, limit = Limit.single)
                .single()
                .variants
                .single()
                .isOriginalVariant shouldBe true
        }

    @Test
    fun `future metadata is readable across repository paths and preserved through updates`() =
        runTest {
            val asset =
                repository
                    .storeNew(createPendingAsset(lqips = LQIPs("original-blurhash", "original-thumbhash")))
                    .markReady(Instant.now())
            repository.markReady(asset)
            val original = asset.variants.single()
            val attributes =
                Attributes(
                    width = 50.toDimension(),
                    height = 40.toDimension(),
                    format = ImageFormat.PIXELS,
                    colorSpace = ColorSpace.SRGB,
                    pixels = PixelAttributes("RGB".toPixelChannels(), PixelLayout.INTERLEAVED, PixelDataType.UINT8),
                )
            val derived =
                repository.storeNewVariant(
                    createPendingVariant(
                        assetId = asset.id,
                        attributes = attributes,
                        transformation = Transformation.fromAttributes(attributes),
                        lqip = LQIPs("derived-blurhash", "derived-thumbhash"),
                    ),
                )
            repository.markUploaded(derived.markReady(Instant.now()), CacheProperties())
            val variants = listOf(derived, original)
            val storedMetadata =
                variants.associate { variant ->
                    val record = dslContext.selectFrom(ASSET_VARIANT).where(ASSET_VARIANT.ID.eq(variant.id.value)).awaitSingle()
                    val known = postgresJson.parseToJsonElement(checkNotNull(record.attributes).data()).jsonObject
                    val nested = known["pixels"]?.let { JsonObject(it.jsonObject + ("futurePixelAttribute" to JsonPrimitive(true))) }
                    val futureAttributes =
                        JSONB.valueOf(
                            JsonObject(
                                known +
                                    ("futureAttribute" to JsonPrimitive(true)) +
                                    (nested?.let { mapOf("pixels" to it) } ?: emptyMap()),
                            ).toString(),
                        )
                    val futureLqips =
                        JSONB.valueOf(
                            JsonObject(
                                postgresJson.parseToJsonElement(checkNotNull(record.lqip).data()).jsonObject +
                                    ("futurePreview" to JsonPrimitive("preview")),
                            ).toString(),
                        )
                    dslContext
                        .update(ASSET_VARIANT)
                        .set(ASSET_VARIANT.ATTRIBUTES, futureAttributes)
                        .set(ASSET_VARIANT.LQIP, futureLqips)
                        .where(ASSET_VARIANT.ID.eq(variant.id.value))
                        .awaitSingle()
                    variant.id to (futureAttributes to futureLqips)
                }
            val expected = variants.map { Triple(it.id, it.attributes, it.lqips) }
            val loaded = repository.fetchForUpdate(asset.path, asset.entryId!!) as Asset.Ready
            val reads =
                listOf(
                    repository.fetchByPath(asset.path, asset.entryId)!!.variants.map { Triple(it.id, it.attributes, it.lqips) },
                    repository
                        .fetchAllByPath(asset.path, limit = Limit.single)
                        .single()
                        .variants
                        .map { Triple(it.id, it.attributes, it.lqips) },
                    repository.fetchForDelivery(asset.path, asset.entryId)!!.variants.map { Triple(it.id, it.attributes, it.lqips) },
                    repository.fetchVariantsForDelivery(asset.id).map { Triple(it.id, it.attributes, it.lqips) },
                    loaded.variants.map { Triple(it.id, it.attributes, it.lqips) },
                )
            reads.forEach { it shouldBe expected }
            val uploaded = repository.markUploaded(derived.markReady(Instant.now()), CacheProperties())
            Triple(uploaded.id, uploaded.attributes, uploaded.lqips) shouldBe expected.first()
            repository.update(loaded.update(alt = "updated", labels = emptyMap(), tags = emptySet()))
            storedMetadata.forEach { (id, metadata) ->
                val record = dslContext.selectFrom(ASSET_VARIANT).where(ASSET_VARIANT.ID.eq(id.value)).awaitSingle()
                (record.attributes to record.lqip) shouldBe metadata
            }
        }

    @Nested
    inner class DeleteCascadeTests {
        @Test
        fun `deleting an asset cascades to all tables`() =
            runTest {
                val ready =
                    repository
                        .storeNew(createPendingAsset())
                        .markReady(Instant.now())
                        .also { repository.markReady(it) }
                repository.deleteByPath(
                    path = AssetPath("/users/123"),
                    entryId = 0L.toEntryId(),
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
                        .markReady(Instant.now())
                        .also { repository.markReady(it) }
                repository.deleteByPath(
                    path = AssetPath("/users/123"),
                    entryId = 0L.toEntryId(),
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
                        .markReady(Instant.now())
                        .also { repository.markReady(it) }
                repository.deleteAllByPath(
                    path = AssetPath("/users/123"),
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
                        .markReady(Instant.now())
                        .also { repository.markReady(it) }
                repository.deleteRecursivelyByPath(
                    path = AssetPath("/users/123"),
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
