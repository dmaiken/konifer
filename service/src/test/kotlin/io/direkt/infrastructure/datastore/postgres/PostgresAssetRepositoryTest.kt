package io.direkt.infrastructure.datastore.postgres

import direkt.jooq.tables.references.ASSET_LABEL
import direkt.jooq.tables.references.ASSET_TAG
import direkt.jooq.tables.references.ASSET_TREE
import direkt.jooq.tables.references.ASSET_VARIANT
import io.direkt.domain.ports.AssetRepository
import io.direkt.infrastructure.datastore.AssetRepositoryTest
import io.direkt.infrastructure.datastore.createPendingAsset
import io.direkt.service.context.OrderBy
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.test.runTest
import org.jooq.DSLContext
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
                val deleteResponse =
                    repository.deleteByPath(
                        path = "/users/123",
                        entryId = 0,
                    )
                deleteResponse shouldHaveSize 1
                deleteResponse.first().apply {
                    bucket shouldBe ready.variants.first().objectStoreBucket
                    key shouldBe ready.variants.first().objectStoreKey
                }

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
}
