package io.konifer.domain.context

import io.konifer.domain.asset.AssetLabels
import io.konifer.domain.context.selector.DeleteSelection

data class DeleteRequestContext(
    val path: String,
    val selection: DeleteSelection,
    val labels: AssetLabels,
) : RequestContext
