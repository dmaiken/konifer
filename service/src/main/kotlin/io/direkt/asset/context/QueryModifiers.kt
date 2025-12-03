package io.direkt.asset.context

val DEFAULT_RETURN_FORMAT = ReturnFormat.LINK
val DEFAULT_ORDER_BY = OrderBy.CREATED
const val DEFAULT_LIMIT = 1

data class QueryModifiers(
    val returnFormat: ReturnFormat = DEFAULT_RETURN_FORMAT,
    val orderBy: OrderBy = DEFAULT_ORDER_BY,
    val limit: Int = DEFAULT_LIMIT,
    val entryId: Long? = null,
    val specifiedModifiers: SpecifiedInRequest = SpecifiedInRequest(),
)

data class SpecifiedInRequest(
    val returnFormat: Boolean = false,
    val orderBy: Boolean = false,
    val limit: Boolean = false,
)
