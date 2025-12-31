package io.direkt.infrastructure.datastore.postgres.scheduling

import direkt.jooq.tables.references.ASSET_VARIANT
import io.direkt.domain.image.ImageFormat
import io.direkt.domain.ports.AssetRepository
import io.direkt.domain.variant.Transformation
import io.direkt.infrastructure.datastore.createPendingAsset
import io.direkt.infrastructure.datastore.createPendingVariant
import io.direkt.infrastructure.datastore.postgres.PostgresAssetRepository
import io.direkt.infrastructure.datastore.postgres.createR2dbcDslContext
import io.direkt.infrastructure.datastore.postgres.postgresContainer
import io.direkt.infrastructure.datastore.postgres.truncateTables
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.inspectors.forAtLeastOne
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
class FailedVariantSweeperTest {
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
    fun `deletes failed variants and schedules reaping of variant`() =
        runTest {
            val ready =
                assetRepository
                    .storeNew(createPendingAsset())
                    .markReady(LocalDateTime.now())
                    .also { assetRepository.markReady(it) }
            val pendingVariant =
                createPendingVariant(
                    assetId = ready.id,
                    transformation =
                        Transformation(
                            height = 500,
                            width = 100,
                            format = ImageFormat.PNG,
                        ),
                ).let {
                    assetRepository.storeNewVariant(it)
                }

            FailedVariantSweeper.invoke(dslContext, olderThan = Duration.ZERO)

            // Variant is not ready so we must query for it directly
            dslContext
                .select(ASSET_VARIANT.ID)
                .from(ASSET_VARIANT)
                .where(ASSET_VARIANT.ID.eq(pendingVariant.id.value))
                .awaitFirstOrNull() shouldBe null

            val event = fetchOutboxReaperEvents(dslContext, 1).first()
            event.objectStoreBucket shouldBe pendingVariant.objectStoreBucket
            event.objectStoreKey shouldBe pendingVariant.objectStoreKey
        }

    @Test
    fun `skips variants that are ready`() =
        runTest {
            val ready =
                assetRepository
                    .storeNew(createPendingAsset())
                    .markReady(LocalDateTime.now())
                    .also { assetRepository.markReady(it) }
            val transformation =
                Transformation(
                    height = 500,
                    width = 100,
                    format = ImageFormat.PNG,
                )
            val readyVariant =
                createPendingVariant(
                    assetId = ready.id,
                    transformation = transformation,
                ).let {
                    assetRepository.storeNewVariant(it)
                }.also {
                    assetRepository.markUploaded(it.markReady(LocalDateTime.now()))
                }

            FailedVariantSweeper.invoke(dslContext, olderThan = Duration.ZERO)

            val assetData =
                assetRepository.fetchByPath(
                    path = ready.path,
                    entryId = ready.entryId,
                    transformation = null,
                )

            assetData shouldNotBe null
            assetData!!.variants.forAtLeastOne {
                it.transformation shouldBe transformation
                it.id shouldBe readyVariant.id
            }

            fetchOutboxReaperEvents(dslContext, 0)
        }

    @Test
    fun `skips variants that have not been in failed state for long enough`() =
        runTest {
            val ready =
                assetRepository
                    .storeNew(createPendingAsset())
                    .markReady(LocalDateTime.now())
                    .also { assetRepository.markReady(it) }
            val pendingVariant =
                createPendingVariant(
                    assetId = ready.id,
                    transformation =
                        Transformation(
                            height = 500,
                            width = 100,
                            format = ImageFormat.PNG,
                        ),
                ).let {
                    assetRepository.storeNewVariant(it)
                }

            FailedVariantSweeper.invoke(dslContext, olderThan = Duration.ofMinutes(1))

            // Variant is not ready so we must query for it directly
            dslContext
                .select(ASSET_VARIANT.ID)
                .from(ASSET_VARIANT)
                .where(ASSET_VARIANT.ID.eq(pendingVariant.id.value))
                .awaitFirstOrNull() shouldNotBe null

            fetchOutboxReaperEvents(dslContext, 0)
        }

    @Test
    fun `if one variant has failed to be deleted then the rest are still attempted`() =
        runTest {
            mockkStatic("org.jooq.kotlin.coroutines.CoroutineExtensionsKt")
            val readyAsset1 =
                assetRepository
                    .storeNew(createPendingAsset())
                    .markReady(LocalDateTime.now())
                    .also { assetRepository.markReady(it) }
            val pendingVariant1 =
                createPendingVariant(
                    assetId = readyAsset1.id,
                    transformation =
                        Transformation(
                            height = 500,
                            width = 100,
                            format = ImageFormat.PNG,
                        ),
                ).let {
                    assetRepository.storeNewVariant(it)
                }
            val readyAsset2 =
                assetRepository
                    .storeNew(createPendingAsset())
                    .markReady(LocalDateTime.now())
                    .also { assetRepository.markReady(it) }
            val pendingVariant2 =
                createPendingVariant(
                    assetId = readyAsset2.id,
                    transformation =
                        Transformation(
                            height = 500,
                            width = 100,
                            format = ImageFormat.PNG,
                        ),
                ).let {
                    assetRepository.storeNewVariant(it)
                }

            // Mock a failure on the first delete
            coEvery {
                dslContext.transactionCoroutine(any<suspend (Configuration) -> Any?>())
            } throws RuntimeException() andThenAnswer { callOriginal() }

            FailedVariantSweeper.invoke(dslContext, olderThan = Duration.ZERO)

            Flux
                .from(
                    dslContext
                        .select(ASSET_VARIANT.ID)
                        .from(ASSET_VARIANT)
                        .where(ASSET_VARIANT.ID.`in`(pendingVariant1.id.value, pendingVariant2.id.value)),
                ).asFlow()
                .toList() shouldHaveSize 1

            val event = fetchOutboxReaperEvents(dslContext, 1).first()
            event.objectStoreBucket shouldBeIn
                listOf(
                    pendingVariant1.objectStoreBucket,
                    pendingVariant2.objectStoreBucket,
                )
            event.objectStoreKey shouldBeIn
                listOf(
                    pendingVariant1.objectStoreKey,
                    pendingVariant2.objectStoreKey,
                )
        }

    @Test
    fun `does not fail if nothing needs to be deleted`() =
        runTest {
            shouldNotThrowAny {
                FailedVariantSweeper.invoke(dslContext, olderThan = Duration.ZERO)
            }
        }
}
