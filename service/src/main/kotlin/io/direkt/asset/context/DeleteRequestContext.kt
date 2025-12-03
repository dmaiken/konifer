package io.direkt.asset.context

data class DeleteRequestContext(
    val path: String,
    val modifiers: DeleteModifiers,
)
