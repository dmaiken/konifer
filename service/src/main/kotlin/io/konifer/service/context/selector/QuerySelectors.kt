package io.konifer.service.context.selector

import io.konifer.service.context.InvalidQuerySelectorsException

val DEFAULT_RETURN_FORMAT = ReturnFormat.LINK
val DEFAULT_ORDER_BY = Order.NEW
const val DEFAULT_LIMIT = 1
const val LIMIT_PARAMETER = "limit"

data class QuerySelectors(
    val returnFormat: ReturnFormat = DEFAULT_RETURN_FORMAT,
    val order: Order = DEFAULT_ORDER_BY,
    val limit: Int = DEFAULT_LIMIT,
    val entryId: Long? = null,
    val specifiedModifiers: SpecifiedInRequest = SpecifiedInRequest(),
) {
    init {
        if (returnFormat != ReturnFormat.METADATA && limit > 1) {
            throw InvalidQuerySelectorsException(
                "Cannot have limit > 1 with return format of: ${returnFormat.name.lowercase()}",
            )
        }
    }
}

data class SpecifiedInRequest(
    val returnFormat: Boolean = false,
    val orderBy: Boolean = false,
    val limit: Boolean = false,
)
