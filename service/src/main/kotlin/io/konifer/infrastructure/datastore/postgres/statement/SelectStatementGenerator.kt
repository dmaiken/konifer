package io.konifer.infrastructure.datastore.postgres.statement

import io.konifer.common.selector.Order
import io.konifer.domain.asset.AssetId
import io.konifer.domain.context.Limit
import io.konifer.domain.variant.VariantSelection
import io.konifer.infrastructure.datastore.postgres.LtreePathAdapter
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
import org.jooq.Record
import org.jooq.SelectFinalStep
import org.jooq.impl.DSL

object SelectStatementGenerator {
    context(trx: DSLContext)
    fun fetchVariants(
        assetId: AssetId,
        selection: VariantSelection,
    ) = trx
        .selectFrom(ASSET_VARIANT)
        .where(ASSET_VARIANT.ASSET_ID.eq(assetId.value))
        .and(ASSET_VARIANT.UPLOADED_AT.isNotNull)
        .and(variantSelectionCondition(selection))
        .orderBy(ASSET_VARIANT.CREATED_AT.desc())

    context(trx: DSLContext)
    fun fetch(
        path: String,
        entryId: Long?,
        selection: VariantSelection,
        order: Order,
        labels: Map<String, String>,
        limit: Limit,
        include: AssetDataToInclude,
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
                onlyReady = include.onlyReady,
            )
        val variantsField = multisetVariantField(trx, selection)
        val labelsField = if (include.includeLabels) multisetLabels(trx) else null
        val tagsField = if (include.includeTags) multisetTags(trx) else null

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
                if (!limit.isUnlimited) {
                    it.limit(limit.value)
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
        selection: VariantSelection,
    ) = DSL
        .multiset(
            context
                .select(*ASSET_VARIANT.fields())
                .from(ASSET_VARIANT)
                .where(ASSET_VARIANT.ASSET_ID.eq(ASSET_TREE.ID))
                .and(ASSET_VARIANT.UPLOADED_AT.isNotNull)
                .and(variantSelectionCondition(selection))
                .orderBy(ASSET_VARIANT.CREATED_AT.desc()),
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

    private fun variantSelectionCondition(selection: VariantSelection): Condition =
        when (selection) {
            VariantSelection.ALL -> DSL.noCondition()
            VariantSelection.ORIGINAL_ONLY -> ASSET_VARIANT.ORIGINAL_VARIANT.eq(true)
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
    val labels: Field<List<AssetLabelRecord>>?,
    val tags: Field<List<AssetTagRecord>>?,
)
