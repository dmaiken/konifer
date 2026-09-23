package io.konifer.infrastructure.datastore.postgres.statement

import io.konifer.common.image.ImageFormat
import io.konifer.domain.asset.Asset
import io.konifer.domain.asset.AssetId
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.transformation.Transformation
import io.konifer.domain.transformation.toDimension
import io.konifer.domain.variant.Variant
import io.konifer.domain.variant.VariantId
import io.konifer.domain.variant.retention.CacheProperties
import io.konifer.infrastructure.datastore.createPendingAsset
import io.konifer.infrastructure.datastore.createPendingVariant
import io.konifer.infrastructure.datastore.postgres.PostgresContainerizedTest
import io.konifer.infrastructure.datastore.postgres.scheduling.fetchVariantDeletedEvents
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import konifer.jooq.tables.references.ASSET_VARIANT
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneOffset.UTC
import kotlin.time.Duration.Companion.hours

class VariantEvictionHelperTest : PostgresContainerizedTest() {
    @Test
    fun `evicts the variant with the lowest decayed access score`() =
        runTest {
            val asset = createReadyAsset()
            val olderPopularVariant = createReadyVariant(asset.id, width = 200)
            val recentlyPopularVariant = createReadyVariant(asset.id, width = 300)
            val scoreAsOf = LocalDateTime.now(UTC)

            setAccessScore(
                variantId = olderPopularVariant.id,
                score = 8.0,
                scoreAsOf = scoreAsOf.minusHours(5),
            )
            setAccessScore(
                variantId = recentlyPopularVariant.id,
                score = 2.0,
                scoreAsOf = scoreAsOf,
            )

            val newlyUploadedVariant =
                persistPendingVariant(asset.id, width = 400)
                    .markReady(LocalDateTime.now(UTC))
                    .also {
                        assetRepository.markUploaded(
                            variant = it,
                            cacheProperties = CacheProperties(maxVariants = 2, accessScoreHalfLife = 1.hours),
                        )
                    }

            fetchNonOriginalVariantIds(asset.id) shouldContainExactlyInAnyOrder
                listOf(recentlyPopularVariant.id, newlyUploadedVariant.id)
            fetchVariantDeletedEvents(dslContext, expectAmount = 1).single().let {
                it.objectStoreBucket shouldBe olderPopularVariant.objectStoreBucket
                it.objectStoreKey shouldBe olderPopularVariant.objectStoreKey
            }
        }

    @Test
    fun `evicts every ready variant beyond the configured limit`() =
        runTest {
            val asset = createReadyAsset()
            val existingVariants =
                (1..4).map { index ->
                    createReadyVariant(asset.id, width = 100 + index).also {
                        setAccessScore(
                            variantId = it.id,
                            score = index.toDouble(),
                            scoreAsOf = LocalDateTime.now(UTC),
                        )
                    }
                }

            val newlyUploadedVariant =
                persistPendingVariant(asset.id, width = 500)
                    .markReady(LocalDateTime.now(UTC))
                    .also {
                        assetRepository.markUploaded(
                            variant = it,
                            cacheProperties = CacheProperties(maxVariants = 2),
                        )
                    }

            fetchNonOriginalVariantIds(asset.id) shouldContainExactlyInAnyOrder
                listOf(existingVariants.last().id, newlyUploadedVariant.id)
            fetchVariantDeletedEvents(dslContext, expectAmount = 3)
                .map { it.objectStoreKey }
                .toSet() shouldBe existingVariants.dropLast(1).map { it.objectStoreKey }.toSet()
        }

    @Test
    fun `does not count pending or original variants toward the limit`() =
        runTest {
            val asset = createReadyAsset()
            val pendingVariant = persistPendingVariant(asset.id, width = 600)
            val newlyUploadedVariant =
                persistPendingVariant(asset.id, width = 700)
                    .markReady(LocalDateTime.now(UTC))
                    .also {
                        assetRepository.markUploaded(
                            variant = it,
                            cacheProperties = CacheProperties(maxVariants = 1),
                        )
                    }

            fetchVariantIds(asset.id) shouldContainExactlyInAnyOrder
                listOf(asset.variants.single().id, pendingVariant.id, newlyUploadedVariant.id)
            fetchVariantDeletedEvents(dslContext, expectAmount = 0)
        }

    private suspend fun createReadyAsset(): Asset.Ready =
        assetRepository
            .storeNew(createPendingAsset())
            .markReady(LocalDateTime.now(UTC))
            .also { assetRepository.markReady(it) }

    private suspend fun createReadyVariant(
        assetId: AssetId,
        width: Int,
    ): Variant.Ready =
        persistPendingVariant(assetId, width)
            .markReady(LocalDateTime.now(UTC))
            .also {
                assetRepository.markUploaded(
                    variant = it,
                    cacheProperties = CacheProperties(maxVariants = 100),
                )
            }

    private suspend fun persistPendingVariant(
        assetId: AssetId,
        width: Int,
    ): Variant.Pending =
        assetRepository.storeNewVariant(
            createPendingVariant(
                assetId = assetId,
                transformation =
                    Transformation(
                        width = width.toDimension(),
                        height = 100.toDimension(),
                        format = ImageFormat.PNG,
                        colorSpace = ColorSpace.SRGB,
                    ),
            ),
        )

    private suspend fun setAccessScore(
        variantId: VariantId,
        score: Double,
        scoreAsOf: LocalDateTime,
    ) {
        dslContext
            .update(ASSET_VARIANT)
            .set(ASSET_VARIANT.ACCESS_SCORE, score)
            .set(ASSET_VARIANT.ACCESS_SCORE_AS_OF, scoreAsOf)
            .where(ASSET_VARIANT.ID.eq(variantId.value))
            .awaitFirstOrNull()
    }

    private suspend fun fetchVariantIds(assetId: AssetId): List<VariantId> =
        dslContext
            .select(ASSET_VARIANT.ID)
            .from(ASSET_VARIANT)
            .where(ASSET_VARIANT.ASSET_ID.eq(assetId.value))
            .asFlow()
            .map { VariantId(checkNotNull(it.value1())) }
            .toList()

    private suspend fun fetchNonOriginalVariantIds(assetId: AssetId): List<VariantId> =
        dslContext
            .select(ASSET_VARIANT.ID)
            .from(ASSET_VARIANT)
            .where(ASSET_VARIANT.ASSET_ID.eq(assetId.value))
            .and(ASSET_VARIANT.ORIGINAL_VARIANT.eq(false))
            .asFlow()
            .map { VariantId(checkNotNull(it.value1())) }
            .toList()
}
