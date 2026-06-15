package io.konifer.infrastructure.datastore.postgres

import io.konifer.infrastructure.datastore.postgres.scheduling.VariantDeletedEvent
import konifer.jooq.tables.references.ASSET_TREE
import konifer.jooq.tables.references.ASSET_VARIANT
import konifer.jooq.tables.references.OUTBOX
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.jooq.CommonTableExpression
import org.jooq.DSLContext
import org.jooq.Record1
import org.jooq.impl.DSL
import org.jooq.kotlin.coroutines.transactionCoroutine
import java.util.UUID

object DeleteAssetHelper {
    suspend fun deleteAssets(
        dslContext: DSLContext,
        deleteIdentificationCte: CommonTableExpression<Record1<UUID?>>,
    ): Int =
        dslContext.transactionCoroutine { trx ->
            // Delete ALL variants belonging to the identified trees.
            val deletedVariants =
                DSL.name("deleted_variants").`as`(
                    DSL
                        .deleteFrom(ASSET_VARIANT)
                        .where(
                            ASSET_VARIANT.ASSET_ID.`in`(
                                DSL.select(deleteIdentificationCte.field(ASSET_TREE.ID)).from(deleteIdentificationCte),
                            ),
                        ).returning(
                            ASSET_VARIANT.ASSET_ID,
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

            // Run the logic and return the distinct deleted parentIds
            val processedParentIds =
                trx
                    .dsl()
                    .with(deleteIdentificationCte)
                    .with(deletedVariants)
                    .with(insertedOutbox)
                    .selectDistinct(deletedVariants.field(ASSET_VARIANT.ASSET_ID))
                    .from(deletedVariants)
                    .asFlow()
                    .map { it.value1() }
                    .toList()

            processedParentIds
                .takeIf { it.isNotEmpty() }
                ?.let { ids ->
                    trx
                        .dsl()
                        .deleteFrom(ASSET_TREE)
                        .where(ASSET_TREE.ID.`in`(ids))
                        .awaitFirstOrNull()
                } ?: 0
        }
}
