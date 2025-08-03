package io.asset.context

data class QueryModifiers(
    val returnFormat: ReturnFormat = ReturnFormat.LINK,
    val orderBy: OrderBy = OrderBy.CREATED,
    val limit: Int = 1,
    val entryId: Long? = null,
)
