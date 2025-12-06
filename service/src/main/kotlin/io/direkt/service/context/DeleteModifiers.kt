package io.direkt.service.context

import io.direkt.domain.asset.DeleteMode

data class DeleteModifiers(
    val mode: DeleteMode = DeleteMode.SINGLE,
    val entryId: Long? = null,
)
