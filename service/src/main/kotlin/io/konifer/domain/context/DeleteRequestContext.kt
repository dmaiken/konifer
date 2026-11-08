package io.konifer.domain.context

import io.konifer.domain.asset.AssetLabels
import io.konifer.domain.asset.AssetPath
import io.konifer.domain.context.selector.DeleteSelection

data class DeleteRequestContext(
    val path: AssetPath,
    val selection: DeleteSelection,
    val labels: AssetLabels,
) : RequestContext
