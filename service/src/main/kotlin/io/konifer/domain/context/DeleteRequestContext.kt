package io.konifer.domain.context

import io.konifer.domain.context.selector.DeleteSelection

data class DeleteRequestContext(
    val path: String,
    val selection: DeleteSelection,
    val labels: Map<String, String>,
) : RequestContext
