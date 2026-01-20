package io.konifer.infrastructure.datastore.postgres.scheduling

import direkt.jooq.tables.references.ASSET_TREE
import direkt.jooq.tables.references.ASSET_VARIANT
import io.konifer.domain.asset.AssetId
import io.konifer.domain.ports.AssetRepository
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import org.jooq.DSLContext
import reactor.core.publisher.Flux
import java.time.LocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

object FailedAssetSweeper {
    const val TASK_NAME = "failed-asset-sweeper"
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    suspend fun invoke(
        dslContext: DSLContext,
        assetRepository: AssetRepository,
        olderThan: Duration = 5.minutes,
    ) {
        logger.info("Sweeping failed assets...")

        val result =
            Flux
                .from(
                    dslContext
                        .select(ASSET_TREE.ID, ASSET_VARIANT.OBJECT_STORE_BUCKET, ASSET_VARIANT.OBJECT_STORE_KEY)
                        .from(ASSET_TREE)
                        .innerJoin(ASSET_VARIANT)
                        .on(ASSET_TREE.ID.eq(ASSET_VARIANT.ASSET_ID))
                        .where(ASSET_TREE.IS_READY.equal(false))
                        .and(ASSET_VARIANT.ORIGINAL_VARIANT.eq(true))
                        .and(ASSET_TREE.CREATED_AT.lessOrEqual(LocalDateTime.now().minusSeconds(olderThan.inWholeSeconds)))
                        .orderBy(ASSET_TREE.CREATED_AT),
                ).asFlow()
                .map {
                    FailedAsset(
                        assetId = AssetId(checkNotNull(it.get(ASSET_TREE.ID))),
                        objectStoreBucket = it.get(ASSET_VARIANT.OBJECT_STORE_BUCKET),
                        objectStoreKey = it.get(ASSET_VARIANT.OBJECT_STORE_KEY),
                    )
                }.toList()

        logger.info("Found ${result.size} failed assets to sweep")
        var errorCount = 0
        result.forEach { failedAsset ->
            runCatching {
                assetRepository.deleteByAssetId(failedAsset.assetId)
            }.onFailure { e ->
                errorCount++
                logger.error("Failed to delete asset ${failedAsset.assetId} and original variant", e)
            }.getOrNull()
        }

        logger.info("Swept ${result.size - errorCount} failed assets with $errorCount errors")
    }
}

data class FailedAsset(
    val assetId: AssetId,
    val objectStoreBucket: String?,
    val objectStoreKey: String?,
)
