package io.konifer.infrastructure.datastore.postgres.scheduling

import io.konifer.common.image.ImageFormat
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.transformation.Transformation
import io.konifer.domain.transformation.toDimension
import io.konifer.domain.variant.VariantSelection
import io.konifer.domain.variant.retention.CacheProperties
import io.konifer.infrastructure.datastore.createPendingAsset
import io.konifer.infrastructure.datastore.createPendingVariant
import io.konifer.infrastructure.datastore.postgres.PostgresContainerizedTest
import io.konifer.infrastructure.datastore.postgres.PostgresVariantRepository
import io.kotest.inspectors.forExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant

class ExpiredVariantSweeperTest : PostgresContainerizedTest() {
    private val postgresVariantRepository = PostgresVariantRepository(dslContext)

    @Test
    fun `can sweep expired variants`() =
        runTest {
            val ready =
                assetRepository
                    .storeNew(createPendingAsset())
                    .markReady(Instant.now())
                    .also { assetRepository.markReady(it) }
            val expiredVariant =
                createPendingVariant(
                    assetId = ready.id,
                    transformation =
                        Transformation(
                            height = 500.toDimension(),
                            width = 100.toDimension(),
                            format = ImageFormat.PNG,
                            colorSpace = ColorSpace.SRGB,
                        ),
                    expiresAt = Instant.now(),
                ).let { pending ->
                    assetRepository
                        .storeNewVariant(pending)
                        .markReady(Instant.now())
                        .also {
                            assetRepository.markUploaded(
                                variant = it,
                                cacheProperties = CacheProperties(),
                            )
                        }
                }
            val longExpiringVariant =
                createPendingVariant(
                    assetId = ready.id,
                    transformation =
                        Transformation(
                            height = 400.toDimension(),
                            width = 100.toDimension(),
                            format = ImageFormat.PNG,
                            colorSpace = ColorSpace.SRGB,
                        ),
                    expiresAt = Instant.now().plus(1, java.time.temporal.ChronoUnit.DAYS),
                ).let { pending ->
                    assetRepository
                        .storeNewVariant(pending)
                        .markReady(Instant.now())
                        .also {
                            assetRepository.markUploaded(
                                variant = it,
                                cacheProperties = CacheProperties(),
                            )
                        }
                }

            ExpiredVariantSweeper.invoke(postgresVariantRepository)

            val asset =
                assetRepository.fetchByPath(
                    path = ready.path,
                    entryId = ready.entryId,
                    selection = VariantSelection.ALL,
                ) shouldNotBe null

            val variants = asset!!.variants shouldHaveSize 2
            variants.forExactly(1) { it.isOriginalVariant shouldBe true }
            variants.forExactly(1) { it.id shouldBe longExpiringVariant.id }

            val event = fetchVariantDeletedEvents(dslContext, 1).first()
            event.objectStoreBucket shouldBe expiredVariant.objectStoreBucket
            event.objectStoreKey shouldBe expiredVariant.objectStoreKey
        }

    @Test
    fun `deletes nothing if nothing is expired`() =
        runTest {
            val ready =
                assetRepository
                    .storeNew(createPendingAsset())
                    .markReady(Instant.now())
                    .also { assetRepository.markReady(it) }
            val longExpiringVariant =
                createPendingVariant(
                    assetId = ready.id,
                    transformation =
                        Transformation(
                            height = 400.toDimension(),
                            width = 100.toDimension(),
                            format = ImageFormat.PNG,
                            colorSpace = ColorSpace.SRGB,
                        ),
                    expiresAt = Instant.now().plus(1, java.time.temporal.ChronoUnit.DAYS),
                ).let { pending ->
                    assetRepository
                        .storeNewVariant(pending)
                        .markReady(Instant.now())
                        .also {
                            assetRepository.markUploaded(
                                variant = it,
                                cacheProperties = CacheProperties(),
                            )
                        }
                }

            ExpiredVariantSweeper.invoke(postgresVariantRepository)

            val asset =
                assetRepository.fetchByPath(
                    path = ready.path,
                    entryId = ready.entryId,
                    selection = VariantSelection.ALL,
                ) shouldNotBe null

            val variants = asset!!.variants shouldHaveSize 2
            variants.forExactly(1) { it.isOriginalVariant shouldBe true }
            variants.forExactly(1) { it.id shouldBe longExpiringVariant.id }

            fetchVariantDeletedEvents(dslContext, 0)
        }
}
