package io.konifer.infrastructure.datastore.postgres

import io.konifer.infrastructure.datastore.postgres.scheduling.VariantDeletedEvent
import konifer.jooq.tables.references.ASSET_VARIANT
import konifer.jooq.tables.references.OUTBOX
import kotlinx.coroutines.reactive.awaitSingle
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.kotlin.coroutines.transactionCoroutine
import java.time.LocalDateTime
import java.time.ZoneOffset.UTC
import java.util.UUID

class PostgresVariantRepository(
    private val dslContext: DSLContext,
) {
    suspend fun deleteExpiredVariants(): Int {
        val now = LocalDateTime.now(UTC)
        return dslContext.transactionCoroutine { trx ->
            // Delete expired variants
            val deletedVariants =
                DSL.name("deleted_variants").`as`(
                    DSL
                        .deleteFrom(ASSET_VARIANT)
                        .where(
                            ASSET_VARIANT.EXPIRES_AT.lessOrEqual(now),
                        ).returning(
                            ASSET_VARIANT.OBJECT_STORE_BUCKET,
                            ASSET_VARIANT.OBJECT_STORE_KEY,
                        ),
                )

            // Bulk insert the captured data into the outbox.
            val insertedOutbox =
                DSL.name("inserted_outbox").`as`(
                    DSL
                        .insertInto(OUTBOX)
                        .columns(OUTBOX.ID, OUTBOX.EVENT_TYPE, OUTBOX.PAYLOAD, OUTBOX.CREATED_AT)
                        .select(
                            DSL
                                .select(
                                    DSL.function("gen_random_uuid", UUID::class.java),
                                    DSL.inline(VariantDeletedEvent.TYPE),
                                    VariantDeletedEvent.jsonJooqFunction(deletedVariants),
                                    DSL.currentLocalDateTime(),
                                ).from(deletedVariants),
                        ).returning(OUTBOX.ID),
                )

            // Run the logic and return the count of deleted variants
            trx
                .dsl()
                .with(deletedVariants)
                .with(insertedOutbox)
                .selectCount()
                .from(deletedVariants)
                .awaitSingle()
                .value1()
        }
    }
}
