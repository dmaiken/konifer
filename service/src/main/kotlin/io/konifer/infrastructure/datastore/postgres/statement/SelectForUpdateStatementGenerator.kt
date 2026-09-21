package io.konifer.infrastructure.datastore.postgres.statement

import io.konifer.common.selector.Order
import io.konifer.domain.asset.AssetId
import konifer.jooq.tables.references.ASSET_TREE
import org.jooq.DSLContext
import org.jooq.Record1
import org.jooq.Select
import org.jooq.postgres.extensions.types.Ltree
import java.util.UUID

object SelectForUpdateStatementGenerator {
    context(trx: DSLContext)
    fun assetsRecursivelyByPath(
        path: Ltree,
        labels: Map<String, String>,
    ): Select<Record1<UUID?>> =
        trx
            .select(ASSET_TREE.ID)
            .from(ASSET_TREE)
            .where(
                QueryConditionGenerator.assetLabelConditions(
                    condition = ASSET_TREE.PATH.startsWith(path),
                    labels = labels,
                ),
            ).forUpdate()
            .of(ASSET_TREE)

    context(trx: DSLContext)
    fun assetById(assetId: AssetId): Select<Record1<UUID?>> =
        trx
            .select(ASSET_TREE.ID)
            .from(ASSET_TREE)
            .where(ASSET_TREE.ID.eq(assetId.value))
            .forUpdate()
            .of(ASSET_TREE)

    context(trx: DSLContext)
    fun assetByPath(
        path: Ltree,
        entryId: Long,
    ): Select<Record1<UUID?>> =
        trx
            .select(ASSET_TREE.ID)
            .from(ASSET_TREE)
            .where(ASSET_TREE.PATH.eq(path))
            .and(ASSET_TREE.ENTRY_ID.eq(entryId))
            .forUpdate()
            .of(ASSET_TREE)

    context(trx: DSLContext)
    fun assetsByPath(
        path: Ltree,
        labels: Map<String, String>,
        order: Order,
        limit: Int,
    ): Select<Record1<UUID?>> {
        val selection =
            trx
                .select(ASSET_TREE.ID)
                .from(ASSET_TREE)
                .where(
                    QueryConditionGenerator.assetLabelConditions(
                        condition = ASSET_TREE.PATH.eq(path),
                        labels = labels,
                    ),
                ).orderBy(*QueryOrderGenerator.assetOrder(order))

        return if (limit > 0) {
            selection.limit(limit).forUpdate().of(ASSET_TREE)
        } else {
            selection.forUpdate().of(ASSET_TREE)
        }
    }
}
