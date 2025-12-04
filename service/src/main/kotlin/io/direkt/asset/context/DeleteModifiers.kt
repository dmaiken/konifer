package io.direkt.asset.context

import io.direkt.path.DeleteMode

data class DeleteModifiers(
    val mode: DeleteMode = DeleteMode.SINGLE,
    val entryId: Long? = null,
)
