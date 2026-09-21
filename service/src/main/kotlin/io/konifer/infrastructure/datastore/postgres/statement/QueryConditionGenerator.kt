package io.konifer.infrastructure.datastore.postgres.statement

import konifer.jooq.tables.references.ASSET_LABEL
import konifer.jooq.tables.references.ASSET_TREE
import org.jooq.Condition
import org.jooq.impl.DSL

object QueryConditionGenerator {
    fun assetLabelConditions(
        condition: Condition,
        labels: Map<String, String>,
    ): Condition =
        labels.entries.fold(condition) { result, (key, value) ->
            result.and(
                DSL.exists(
                    DSL
                        .selectOne()
                        .from(ASSET_LABEL)
                        .where(ASSET_LABEL.ASSET_ID.eq(ASSET_TREE.ID))
                        .and(ASSET_LABEL.LABEL_KEY.eq(key))
                        .and(ASSET_LABEL.LABEL_VALUE.eq(value)),
                ),
            )
        }
}
