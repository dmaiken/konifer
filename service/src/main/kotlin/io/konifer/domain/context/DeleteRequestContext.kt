package io.konifer.domain.context

import io.konifer.domain.context.selector.DeleteModifiers

data class DeleteRequestContext(
    val path: String,
    val modifiers: DeleteModifiers,
    val labels: Map<String, String>,
)
