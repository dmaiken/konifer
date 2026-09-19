package io.konifer.infrastructure.datastore.postgres.statement

import io.konifer.domain.asset.AssetId
import io.konifer.infrastructure.datastore.postgres.scheduling.VariantDeletedEvent
import konifer.jooq.tables.records.AssetLabelRecord
import konifer.jooq.tables.records.AssetTagRecord
import konifer.jooq.tables.records.AssetTreeRecord
import konifer.jooq.tables.records.AssetVariantRecord
import konifer.jooq.tables.references.ASSET_LABEL
import konifer.jooq.tables.references.ASSET_TAG
import konifer.jooq.tables.references.ASSET_TREE
import konifer.jooq.tables.references.ASSET_VARIANT
import konifer.jooq.tables.references.OUTBOX
import org.jooq.CommonTableExpression
import org.jooq.DSLContext
import org.jooq.DeleteConditionStep
import org.jooq.Record1
import org.jooq.Select
import org.jooq.impl.DSL
import java.util.UUID

object DeleteStatementGenerator {
    context(trx: DSLContext)
    fun deleteVariantsAndEnqueueOutbox(targets: Select<Record1<UUID?>>): Select<Record1<UUID?>> {
        val targetCte = DSL.name("targets").`as`(targets)
        val deletedVariants: CommonTableExpression<AssetVariantRecord> =
            DSL.name("deleted_variants").`as`(
                DSL
                    .deleteFrom(ASSET_VARIANT)
                    .where(
                        ASSET_VARIANT.ASSET_ID.`in`(
                            DSL.select(targetCte.field(ASSET_TREE.ID)).from(targetCte),
                        ),
                    ).returning(
                        ASSET_VARIANT.ASSET_ID,
                        ASSET_VARIANT.OBJECT_STORE_BUCKET,
                        ASSET_VARIANT.OBJECT_STORE_KEY,
                    ),
            )
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

        return trx
            .with(targetCte)
            .with(deletedVariants)
            .with(insertedOutbox)
            .selectDistinct(deletedVariants.field(ASSET_VARIANT.ASSET_ID))
            .from(deletedVariants)
    }

    context(trx: DSLContext)
    fun deleteAssets(assetIds: Collection<UUID>): DeleteConditionStep<AssetTreeRecord> =
        trx
            .deleteFrom(ASSET_TREE)
            .where(ASSET_TREE.ID.`in`(assetIds))

    context(trx: DSLContext)
    fun deleteLabels(assetId: AssetId): DeleteConditionStep<AssetLabelRecord> =
        trx
            .delete(ASSET_LABEL)
            .where(ASSET_LABEL.ASSET_ID.eq(assetId.value))

    context(trx: DSLContext)
    fun deleteTags(assetId: AssetId): DeleteConditionStep<AssetTagRecord> =
        trx
            .delete(ASSET_TAG)
            .where(ASSET_TAG.ASSET_ID.eq(assetId.value))
}
