package io.konifer.service.context.modifiers

import io.konifer.service.context.InvalidDeleteSelectorsException

const val IS_RECURSIVE_DEFAULT = false

data class DeleteModifiers(
    val orderBy: OrderBy = DEFAULT_ORDER_BY,
    val limit: Int = DEFAULT_LIMIT,
    val recursive: Boolean = IS_RECURSIVE_DEFAULT,
    val entryId: Long? = null,
) {
    init {
        if (recursive && limit > DEFAULT_LIMIT) {
            throw InvalidDeleteSelectorsException("Cannot specify limit when performing recursive delete")
        }
    }
}
