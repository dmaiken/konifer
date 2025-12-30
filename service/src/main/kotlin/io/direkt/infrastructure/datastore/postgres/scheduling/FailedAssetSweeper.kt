package io.direkt.infrastructure.datastore.postgres.scheduling

import direkt.jooq.tables.references.ASSET_TREE
import direkt.jooq.tables.references.ASSET_VARIANT
import direkt.jooq.tables.references.OUTBOX
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.serialization.json.Json
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.kotlin.coroutines.transactionCoroutine
import reactor.core.publisher.Flux
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

object FailedAssetSweeper {
    const val TASK_NAME = "failed-asset-sweeper"
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    suspend fun invoke(
        dslContext: DSLContext,
        olderThan: Duration = Duration.ofMinutes(5),
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
                        .and(ASSET_TREE.CREATED_AT.lessOrEqual(LocalDateTime.now().minusSeconds(olderThan.toSeconds())))
                        .orderBy(ASSET_TREE.CREATED_AT),
                ).asFlow()
                .map {
                    FailedAsset(
                        assetId = checkNotNull(it.get(ASSET_TREE.ID)),
                        objectStoreBucket = it.get(ASSET_VARIANT.OBJECT_STORE_BUCKET),
                        objectStoreKey = it.get(ASSET_VARIANT.OBJECT_STORE_KEY),
                    )
                }.toList()

        logger.info("Found ${result.size} failed assets to sweep")
        var failedCount = 0
        result.forEach { failedAsset ->
            runCatching {
                dslContext.transactionCoroutine { trx ->
                    trx
                        .dsl()
                        .deleteFrom(ASSET_TREE)
                        .where(ASSET_TREE.ID.eq(failedAsset.assetId))
                        .awaitFirstOrNull()
                    if (failedAsset.objectStoreBucket != null && failedAsset.objectStoreKey != null) {
                        val event =
                            ReapVariantEvent(
                                objectStoreBucket = failedAsset.objectStoreBucket,
                                objectStoreKey = failedAsset.objectStoreKey,
                            )
                        trx
                            .dsl()
                            .insertInto(OUTBOX)
                            .set(OUTBOX.ID, UUID.randomUUID())
                            .set(OUTBOX.EVENT_TYPE, event.eventType)
                            .set(OUTBOX.PAYLOAD, JSONB.valueOf(Json.encodeToString(event)))
                            .set(OUTBOX.CREATED_AT, LocalDateTime.now())
                            .awaitFirstOrNull()
                    }
                }
            }.onFailure { e ->
                failedCount++
                logger.error("Failed to delete asset ${failedAsset.assetId} with original variant", e)
            }.getOrNull()
        }

        logger.info("Swept ${result.size - failedCount} failed assets with $failedCount failures")
    }
}

data class FailedAsset(
    val assetId: UUID,
    val objectStoreBucket: String?,
    val objectStoreKey: String?,
)
