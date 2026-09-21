package io.konifer.infrastructure.datastore.postgres.statement

import io.konifer.common.selector.Order
import io.konifer.domain.transformation.Transformation
import io.konifer.infrastructure.datastore.postgres.LtreePathAdapter
import io.konifer.infrastructure.datastore.postgres.VariantParameterGenerator
import konifer.jooq.tables.records.AssetLabelRecord
import konifer.jooq.tables.records.AssetTagRecord
import konifer.jooq.tables.records.AssetVariantRecord
import konifer.jooq.tables.references.ASSET_LABEL
import konifer.jooq.tables.references.ASSET_TAG
import konifer.jooq.tables.references.ASSET_TREE
import konifer.jooq.tables.references.ASSET_VARIANT
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.JSONB
import org.jooq.Record
import org.jooq.SelectFinalStep
import org.jooq.impl.DSL

object SelectStatementGenerator {
    context(trx: DSLContext)
    fun fetch(
        path: String,
        entryId: Long?,
        transformation: Transformation?,
        order: Order,
        labels: Map<String, String>,
        includeOnlyReady: Boolean,
        limit: Int,
    ): MultiSetSelectHandles {
        val treePath = LtreePathAdapter.toTreePathFromUriPath(path)
        val entryIdCondition =
            entryId?.let {
                ASSET_TREE.ENTRY_ID.eq(entryId)
            } ?: DSL.noCondition()
        val assetOrderConditions = QueryOrderGenerator.assetOrder(order)
        val whereCondition =
            includeReadyConditions(
                whereCondition =
                    QueryConditionGenerator.assetLabelConditions(
                        condition = ASSET_TREE.PATH.eq(treePath),
                        labels = labels,
                    ),
                onlyReady = includeOnlyReady,
            )
        val variantsField = multisetVariantField(trx, transformation)
        val labelsField = multisetLabels(trx)
        val tagsField = multisetTags(trx)

        return trx
            .select(
                *ASSET_TREE.fields(),
                variantsField,
                labelsField,
                tagsField,
            ).from(ASSET_TREE)
            .where(whereCondition)
            .and(entryIdCondition)
            .orderBy(*assetOrderConditions)
            .let {
                if (limit > 0) {
                    it.limit(limit)
                } else {
                    it
                }
            }.let {
                MultiSetSelectHandles(
                    statement = it,
                    variants = variantsField,
                    labels = labelsField,
                    tags = tagsField,
                )
            }
    }

    private fun multisetVariantField(
        context: DSLContext,
        transformation: Transformation?,
    ) = DSL
        .multiset(
            context
                .select(*ASSET_VARIANT.fields())
                .from(ASSET_VARIANT)
                .where(ASSET_VARIANT.ASSET_ID.eq(ASSET_TREE.ID))
                .and(ASSET_VARIANT.UPLOADED_AT.isNotNull)
                .and(
                    transformation?.let {
                        calculateJoinVariantConditions(it)
                    } ?: DSL.noCondition(),
                ).orderBy(ASSET_VARIANT.CREATED_AT.desc()),
        ).convertFrom { records ->
            records.map { r -> r.into(AssetVariantRecord::class.java) }
        }.`as`("variants")

    private fun multisetLabels(context: DSLContext) =
        DSL
            .multiset(
                context
                    .select(*ASSET_LABEL.fields())
                    .from(ASSET_LABEL)
                    .where(ASSET_LABEL.ASSET_ID.eq(ASSET_TREE.ID)),
            ).convertFrom { records ->
                records.map { r -> r.into(AssetLabelRecord::class.java) }
            }.`as`("labels")

    private fun multisetTags(context: DSLContext) =
        DSL
            .multiset(
                context
                    .select(*ASSET_TAG.fields())
                    .from(ASSET_TAG)
                    .where(ASSET_TAG.ASSET_ID.eq(ASSET_TREE.ID)),
            ).convertFrom { records ->
                records.map { r -> r.into(AssetTagRecord::class.java) }
            }.`as`("tags")

    private fun calculateJoinVariantConditions(transformation: Transformation): Condition {
        val condition = ASSET_VARIANT.ASSET_ID.eq(ASSET_TREE.ID)
        return if (transformation.originalVariant) {
            condition.and(ASSET_VARIANT.ORIGINAL_VARIANT).eq(true)
        } else {
            val serializedTransformation =
                VariantParameterGenerator.generateImageVariantTransformations(
                    transformation,
                )
            condition
                .and(
                    ASSET_VARIANT.TRANSFORMATION.eq(JSONB.valueOf(serializedTransformation)),
                )
        }
    }

    private fun includeReadyConditions(
        whereCondition: Condition,
        onlyReady: Boolean,
    ): Condition =
        if (onlyReady) {
            whereCondition.and(ASSET_TREE.IS_READY.eq(true))
        } else {
            whereCondition
        }
}

data class MultiSetSelectHandles(
    val statement: SelectFinalStep<Record>,
    val variants: Field<List<AssetVariantRecord>>,
    val labels: Field<List<AssetLabelRecord>>,
    val tags: Field<List<AssetTagRecord>>,
)
