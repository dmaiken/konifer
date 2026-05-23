package io.konifer.domain.context

data class UpdateRequestContext(
    val path: String,
    val entryId: Long,
)
