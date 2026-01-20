package io.direkt.service.context.modifiers

const val IS_RECURSIVE_DEFAULT = false

data class DeleteModifiers(
    val orderBy: OrderBy = DEFAULT_ORDER_BY,
    val limit: Int = DEFAULT_LIMIT,
    val recursive: Boolean = IS_RECURSIVE_DEFAULT,
    val entryId: Long? = null,
)
