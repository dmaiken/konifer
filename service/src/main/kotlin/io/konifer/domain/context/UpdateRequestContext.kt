package io.konifer.domain.context

import io.konifer.domain.asset.AssetPath

data class UpdateRequestContext(
    val path: AssetPath,
    val entryId: Long,
) : RequestContext
