package io.direkt.service.context

data class DeleteRequestContext(
    val path: String,
    val modifiers: DeleteModifiers,
    val labels: Map<String, String>,
)
