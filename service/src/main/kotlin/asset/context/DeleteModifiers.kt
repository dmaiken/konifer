package io.asset.context

import io.path.DeleteMode

data class DeleteModifiers(
    val mode: DeleteMode = DeleteMode.SINGLE,
    val entryId: Long? = null,
)
