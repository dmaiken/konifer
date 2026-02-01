package io.konifer.service.context

import io.konifer.service.context.selector.DeleteModifiers

data class DeleteRequestContext(
    val path: String,
    val modifiers: DeleteModifiers,
    val labels: Map<String, String>,
)
