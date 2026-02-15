package io.konifer.infrastructure.datastore.postgres.scheduling

import io.konifer.domain.ports.ObjectStore
import io.ktor.util.logging.KtorSimpleLogger
import konifer.jooq.tables.references.OUTBOX
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.serialization.json.Json
import org.jooq.DSLContext
import reactor.core.publisher.Flux

object VariantReaper {
    const val TASK_NAME = "reap-variants"
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    suspend fun invoke(
        dslContext: DSLContext,
        objectStore: ObjectStore,
        reapLimit: Int = 1000,
    ) {
        logger.info("Reaping variants...")

        val events =
            Flux
                .from(
                    dslContext
                        .select(OUTBOX.ID, OUTBOX.PAYLOAD)
                        .from(OUTBOX)
                        .where(OUTBOX.EVENT_TYPE.eq(VariantDeletedEvent.TYPE))
                        .orderBy(OUTBOX.CREATED_AT)
                        .limit(reapLimit),
                ).asFlow()
                .mapNotNull {
                    Pair(
                        first = it.get(OUTBOX.ID) ?: return@mapNotNull null,
                        second =
                            it.get(OUTBOX.PAYLOAD)?.let { json ->
                                Json.decodeFromString<VariantDeletedEvent>(json.data())
                            } ?: return@mapNotNull null,
                    )
                }.toList()

        var failedCount = 0
        for ((id, event) in events) {
            try {
                objectStore.delete(
                    bucket = event.objectStoreBucket,
                    key = event.objectStoreKey,
                )
            } catch (e: Exception) {
                logger.error("Failed to delete object in bucket: ${event.objectStoreBucket} with key: ${event.objectStoreKey}", e)
                failedCount++
                continue
            }
            dslContext
                .deleteFrom(OUTBOX)
                .where(OUTBOX.ID.eq(id))
                .awaitFirstOrNull()
        }

        if (events.isNotEmpty()) {
            logger.info("Reaped ${events.size - failedCount} variants with $failedCount failures")
        }
    }
}
