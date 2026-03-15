package io.konifer.client

import io.konifer.common.selector.Order

sealed interface QuerySelectors {
    class OrderBy(
        val orderBy: Order,
    ) : QuerySelectors

    class EntryId(
        val entryId: Long,
    ) : QuerySelectors

    class None : QuerySelectors
}
