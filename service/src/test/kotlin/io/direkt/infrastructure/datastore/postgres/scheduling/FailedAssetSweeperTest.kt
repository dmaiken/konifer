package io.direkt.infrastructure.datastore.postgres.scheduling

import direkt.jooq.tables.references.ASSET_TREE
import direkt.jooq.tables.references.ASSET_VARIANT
import direkt.jooq.tables.references.OUTBOX
import io.direkt.domain.ports.AssetRepository
import io.direkt.infrastructure.datastore.createPendingAsset
import io.direkt.infrastructure.datastore.postgres.PostgresAssetRepository
import io.direkt.infrastructure.datastore.postgres.createR2dbcDslContext
import io.direkt.infrastructure.datastore.postgres.getNonNull
import io.direkt.infrastructure.datastore.postgres.postgresContainer
import io.direkt.infrastructure.datastore.postgres.truncateTables
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.mockkStatic
import io.mockk.spyk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.jooq.Configuration
import org.jooq.DSLContext
import org.jooq.kotlin.coroutines.transactionCoroutine
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import reactor.core.publisher.Flux
import java.time.Duration
import java.time.LocalDateTime

@Testcontainers
class FailedAssetSweeperTest {
    companion object {
        @JvmStatic
        @Container
        private val postgres = postgresContainer()
    }

    val dslContext: DSLContext by lazy { spyk(createR2dbcDslContext(postgres)) }

    val assetRepository: AssetRepository by lazy {
        PostgresAssetRepository(
            dslContext = dslContext,
        )
    }

    @BeforeEach
    fun clearTables() {
        truncateTables(postgres)
    }

    @Test
    fun `deletes failed assets and schedules purging of original variant`() =
        runTest {
            val pending = createPendingAsset()
            val pendingPersisted = assetRepository.storeNew(pending)

            FailedAssetSweeper.invoke(dslContext, olderThan = Duration.ZERO)

            // Asset is not ready so we must query for it directly
            dslContext
                .select(ASSET_TREE.ID)
                .from(ASSET_TREE)
                .where(ASSET_TREE.ID.eq(pendingPersisted.id.value))
                .awaitFirstOrNull() shouldBe null

            val event = fetchOutboxEvents(1).first()
            event.objectStoreBucket shouldBe pendingPersisted.variants.first { it.isOriginalVariant }.objectStoreBucket
            event.objectStoreKey shouldBe pendingPersisted.variants.first { it.isOriginalVariant }.objectStoreKey
        }

    @Test
    fun `skips assets that are ready`() =
        runTest {
            val pending = createPendingAsset()
            val ready =
                assetRepository
                    .storeNew(pending)
                    .markReady(LocalDateTime.now())
                    .also { assetRepository.markReady(it) }

            FailedAssetSweeper.invoke(dslContext, olderThan = Duration.ZERO)

            assetRepository.fetchByPath(
                path = ready.path,
                entryId = ready.entryId,
                transformation = null,
            ) shouldNotBe null
            fetchOutboxEvents(0)
        }

    @Test
    fun `skips assets that have not been in failed state for long enough`() =
        runTest {
            val pending = createPendingAsset()
            val pendingPersisted = assetRepository.storeNew(pending)

            FailedAssetSweeper.invoke(dslContext, olderThan = Duration.ofMinutes(1))

            // Asset is not ready so we must query for it directly
            dslContext
                .select(ASSET_TREE.ID)
                .from(ASSET_TREE)
                .where(ASSET_TREE.ID.eq(pendingPersisted.id.value))
                .awaitFirstOrNull() shouldNotBe null
            fetchOutboxEvents(0)
        }

    @Test
    fun `if asset does not have original variant persisted then asset is still deleted`() =
        runTest {
            val pending = createPendingAsset()
            val pendingPersisted = assetRepository.storeNew(pending)
            // Delete the variant from the DB
            dslContext
                .deleteFrom(ASSET_VARIANT)
                .where(ASSET_VARIANT.ASSET_ID.eq(pendingPersisted.id.value))
                .awaitFirstOrNull()

            FailedAssetSweeper.invoke(dslContext, olderThan = Duration.ZERO)

            assetRepository.fetchByPath(
                path = pendingPersisted.path,
                entryId = pendingPersisted.entryId,
                transformation = null,
            ) shouldBe null

            fetchOutboxEvents(0)
        }

    @Test
    fun `if one asset fails to be deleted then the rest are attempted`() =
        runTest {
            mockkStatic("org.jooq.kotlin.coroutines.CoroutineExtensionsKt")
            val pendingPersisted1 = assetRepository.storeNew(createPendingAsset())
            val pendingPersisted2 = assetRepository.storeNew(createPendingAsset())

            // Mock a failure on the first delete
            coEvery {
                dslContext.transactionCoroutine(any<suspend (Configuration) -> Any?>())
            } throws RuntimeException() andThenAnswer { callOriginal() }

            FailedAssetSweeper.invoke(dslContext, olderThan = Duration.ZERO)

            Flux
                .from(
                    dslContext
                        .select(ASSET_TREE.ID)
                        .from(ASSET_TREE)
                        .where(ASSET_TREE.ID.`in`(pendingPersisted1.id.value, pendingPersisted2.id.value)),
                ).asFlow()
                .toList() shouldHaveSize 1

            val event = fetchOutboxEvents(1).first()
            event.objectStoreBucket shouldBeIn
                listOf(
                    pendingPersisted1.variants.first { it.isOriginalVariant }.objectStoreBucket,
                    pendingPersisted2.variants.first { it.isOriginalVariant }.objectStoreBucket,
                )
            event.objectStoreKey shouldBeIn
                listOf(
                    pendingPersisted1.variants.first { it.isOriginalVariant }.objectStoreKey,
                    pendingPersisted2.variants.first { it.isOriginalVariant }.objectStoreKey,
                )
        }

    @Test
    fun `does not fail if nothing needs to be deleted`() =
        runTest {
            shouldNotThrowAny {
                FailedAssetSweeper.invoke(dslContext, olderThan = Duration.ZERO)
            }
        }

    private suspend fun fetchOutboxEvents(expectAmount: Int): List<ReapVariantEvent> {
        val events =
            Flux
                .from(
                    dslContext
                        .select()
                        .from(OUTBOX),
                ).asFlow()
                .toList()
        events shouldHaveSize expectAmount
        events.forAll {
            it.get(OUTBOX.EVENT_TYPE) shouldBe "REAP_VARIANT"
        }

        return events.map {
            Json.decodeFromString<ReapVariantEvent>(it.getNonNull(OUTBOX.PAYLOAD).data())
        }
    }
}
