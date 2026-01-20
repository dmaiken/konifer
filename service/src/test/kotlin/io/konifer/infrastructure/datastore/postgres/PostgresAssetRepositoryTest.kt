package io.konifer.infrastructure.datastore.postgres

import io.konifer.domain.ports.AssetRepository
import io.konifer.infrastructure.datastore.AssetRepositoryTest
import io.konifer.infrastructure.datastore.createPendingAsset
import io.konifer.infrastructure.datastore.postgres.scheduling.VariantDeletedEvent
import io.konifer.service.context.modifiers.OrderBy
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

    @Nested
    inner class DeleteCascadeTests {
        @Test
        fun `deleting an asset cascades to all tables`() =
            runTest {
                val ready =
                    repository
                        .storeNew(createPendingAsset())
                        .markReady(LocalDateTime.now())
                        .also { repository.markReady(it) }
                repository.deleteByPath(
                    path = "/users/123",
                    entryId = 0,
                )

                repository.fetchByPath(ready.path, ready.entryId, null, OrderBy.CREATED) shouldBe null
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
                        .markReady(LocalDateTime.now())
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
                        .markReady(LocalDateTime.now())
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
                        .markReady(LocalDateTime.now())
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
