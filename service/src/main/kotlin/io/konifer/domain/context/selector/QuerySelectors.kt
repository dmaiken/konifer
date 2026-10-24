package io.konifer.domain.context.selector

import io.konifer.common.selector.DEFAULT_ENTRIES_LIMIT
import io.konifer.common.selector.Order
import io.konifer.common.selector.ReturnFormat
import io.konifer.domain.context.InvalidQuerySelectorsException
import io.konifer.domain.context.Limit
import io.konifer.domain.context.toLimit

val DEFAULT_RETURN_FORMAT = ReturnFormat.LINK
val DEFAULT_ORDER_BY = Order.NEW

data class QuerySelectors(
    val returnFormat: ReturnFormat = DEFAULT_RETURN_FORMAT,
    val order: Order = DEFAULT_ORDER_BY,
    val limit: Limit = if (returnFormat == ReturnFormat.ENTRIES) DEFAULT_ENTRIES_LIMIT.toLimit() else Limit.single,
    val entryId: Long? = null,
    val specifiedModifiers: SpecifiedInRequest = SpecifiedInRequest(),
) {
    init {
        if (returnFormat != ReturnFormat.ENTRIES && limit != Limit.single) {
            throw InvalidQuerySelectorsException(
                "Limit must be 1 with return format of: ${returnFormat.name.lowercase()}",
            )
        }
        if (returnFormat == ReturnFormat.ENTRIES && entryId != null) {
            throw InvalidQuerySelectorsException("Entries cannot be selected by entry ID")
        }
    }
}

data class SpecifiedInRequest(
    val returnFormat: Boolean = false,
    val orderBy: Boolean = false,
    val limit: Boolean = false,
)
