package io.konifer.infrastructure.datastore.postgres.statement

import io.konifer.common.selector.Order
import konifer.jooq.tables.references.ASSET_TREE
import org.jooq.SortField

object QueryOrderGenerator {
    fun assetOrder(order: Order): Array<out SortField<out Comparable<*>?>> {
        val orderField =
            when (order) {
                Order.NEW -> ASSET_TREE.CREATED_AT.desc()
                Order.MODIFIED -> ASSET_TREE.MODIFIED_AT.desc()
            }

        return arrayOf(orderField, ASSET_TREE.ENTRY_ID.desc())
    }
}
