package io.konifer.domain.context

import io.konifer.domain.asset.AssetPath
import io.konifer.domain.asset.EntryId

data class UpdateRequestContext(
    val path: AssetPath,
    val entryId: EntryId,
) : RequestContext
