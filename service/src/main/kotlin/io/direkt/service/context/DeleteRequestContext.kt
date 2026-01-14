package io.direkt.service.context

import io.direkt.service.context.modifiers.DeleteModifiers

data class DeleteRequestContext(
    val path: String,
    val modifiers: DeleteModifiers,
    val labels: Map<String, String>,
)
