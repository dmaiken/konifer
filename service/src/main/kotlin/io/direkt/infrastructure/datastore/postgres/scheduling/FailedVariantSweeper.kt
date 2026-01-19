package io.direkt.infrastructure.datastore.postgres.scheduling

import direkt.jooq.tables.references.ASSET_VARIANT
import direkt.jooq.tables.references.OUTBOX
import io.direkt.infrastructure.datastore.postgres.getNonNull
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

object FailedVariantSweeper {
    const val TASK_NAME = "failed-variant-sweeper"
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    suspend fun invoke(
        dslContext: DSLContext,
        olderThan: Duration = Duration.ofMinutes(5),
    ) {
        logger.info("Sweeping failed variants...")

        val result =
            Flux
                .from(
                    dslContext
                        .select(ASSET_VARIANT.ID, ASSET_VARIANT.OBJECT_STORE_BUCKET, ASSET_VARIANT.OBJECT_STORE_KEY)
                        .from(ASSET_VARIANT)
                        .where(ASSET_VARIANT.UPLOADED_AT.isNull)
                        .and(ASSET_VARIANT.ORIGINAL_VARIANT.eq(false))
                        .and(
                            ASSET_VARIANT.CREATED_AT.lessOrEqual(
                                LocalDateTime.now().minusSeconds(olderThan.toSeconds()),
                            ),
                        ).orderBy(ASSET_VARIANT.CREATED_AT),
                ).asFlow()
                .map {
                    FailedVariant(
                        variantId = it.getNonNull(ASSET_VARIANT.ID),
                        objectStoreBucket = it.getNonNull(ASSET_VARIANT.OBJECT_STORE_BUCKET),
                        objectStoreKey = it.getNonNull(ASSET_VARIANT.OBJECT_STORE_KEY),
                    )
                }.toList()

        logger.info("Found ${result.size} failed variants to sweep")
        var errorCount = 0
        result.forEach { failedVariant ->
            runCatching {
                dslContext.transactionCoroutine { trx ->
                    trx
                        .dsl()
                        .deleteFrom(ASSET_VARIANT)
                        .where(ASSET_VARIANT.ID.eq(failedVariant.variantId))
                        .awaitFirstOrNull()
                    val event =
                        VariantDeletedEvent(
                            objectStoreBucket = failedVariant.objectStoreBucket,
                            objectStoreKey = failedVariant.objectStoreKey,
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
            }.onFailure { e ->
                errorCount++
                logger.error("Failed to delete variant ${failedVariant.variantId}", e)
            }.getOrNull()

            logger.info("Swept ${result.size - errorCount} failed variants with $errorCount errors")
        }
    }
}

data class FailedVariant(
    val variantId: UUID,
    val objectStoreBucket: String,
    val objectStoreKey: String,
)
