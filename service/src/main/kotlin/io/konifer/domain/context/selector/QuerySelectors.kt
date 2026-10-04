package io.konifer.domain.context.selector

import io.konifer.common.selector.DEFAULT_ENTRIES_LIMIT
import io.konifer.common.selector.Order
import io.konifer.common.selector.ReturnFormat
import io.konifer.domain.context.InvalidQuerySelectorsException

val DEFAULT_RETURN_FORMAT = ReturnFormat.LINK
val DEFAULT_ORDER_BY = Order.NEW
const val DEFAULT_LIMIT = 1

data class QuerySelectors(
    val returnFormat: ReturnFormat = DEFAULT_RETURN_FORMAT,
    val order: Order = DEFAULT_ORDER_BY,
    val limit: Int = if (returnFormat == ReturnFormat.ENTRIES) DEFAULT_ENTRIES_LIMIT else DEFAULT_LIMIT,
    val entryId: Long? = null,
    val specifiedModifiers: SpecifiedInRequest = SpecifiedInRequest(),
) {
    init {
        if (returnFormat != ReturnFormat.ENTRIES && limit > 1) {
            throw InvalidQuerySelectorsException(
                "Cannot have limit > 1 with return format of: ${returnFormat.name.lowercase()}",
            )
        }
        if (returnFormat == ReturnFormat.INFO && limit != 1) {
            throw InvalidQuerySelectorsException("Info requests must select a single entry")
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
