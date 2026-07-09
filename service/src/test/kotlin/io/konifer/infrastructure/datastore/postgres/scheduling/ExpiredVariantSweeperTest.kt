package io.konifer.infrastructure.datastore.postgres.scheduling

import io.konifer.common.image.ImageFormat
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.variant.Transformation
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
import java.time.LocalDateTime
import java.time.ZoneOffset.UTC

class ExpiredVariantSweeperTest : PostgresContainerizedTest() {
    private val postgresVariantRepository = PostgresVariantRepository(dslContext)

    @Test
    fun `can sweep expired variants`() =
        runTest {
            val ready =
                assetRepository
                    .storeNew(createPendingAsset())
                    .markReady(LocalDateTime.now(UTC))
                    .also { assetRepository.markReady(it) }
            val expiredVariant =
                createPendingVariant(
                    assetId = ready.id,
                    transformation =
                        Transformation(
                            height = 500,
                            width = 100,
                            format = ImageFormat.PNG,
                            colorSpace = ColorSpace.SRGB,
                        ),
                    expiresAt = LocalDateTime.now(UTC),
                ).let { pending ->
                    assetRepository
                        .storeNewVariant(pending)
                        .markReady(LocalDateTime.now(UTC))
                        .also { assetRepository.markUploaded(it) }
                }
            val longExpiringVariant =
                createPendingVariant(
                    assetId = ready.id,
                    transformation =
                        Transformation(
                            height = 400,
                            width = 100,
                            format = ImageFormat.PNG,
                            colorSpace = ColorSpace.SRGB,
                        ),
                    expiresAt = LocalDateTime.now(UTC).plusDays(1),
                ).let { pending ->
                    assetRepository
                        .storeNewVariant(pending)
                        .markReady(LocalDateTime.now(UTC))
                        .also { assetRepository.markUploaded(it) }
                }

            ExpiredVariantSweeper.invoke(postgresVariantRepository)

            val asset =
                assetRepository.fetchByPath(
                    path = ready.path,
                    entryId = ready.entryId,
                    transformation = null,
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
                    .markReady(LocalDateTime.now(UTC))
                    .also { assetRepository.markReady(it) }
            val longExpiringVariant =
                createPendingVariant(
                    assetId = ready.id,
                    transformation =
                        Transformation(
                            height = 400,
                            width = 100,
                            format = ImageFormat.PNG,
                            colorSpace = ColorSpace.SRGB,
                        ),
                    expiresAt = LocalDateTime.now(UTC).plusDays(1),
                ).let { pending ->
                    assetRepository
                        .storeNewVariant(pending)
                        .markReady(LocalDateTime.now(UTC))
                        .also { assetRepository.markUploaded(it) }
                }

            ExpiredVariantSweeper.invoke(postgresVariantRepository)

            val asset =
                assetRepository.fetchByPath(
                    path = ready.path,
                    entryId = ready.entryId,
                    transformation = null,
                ) shouldNotBe null

            val variants = asset!!.variants shouldHaveSize 2
            variants.forExactly(1) { it.isOriginalVariant shouldBe true }
            variants.forExactly(1) { it.id shouldBe longExpiringVariant.id }

            fetchVariantDeletedEvents(dslContext, 0)
        }
}
