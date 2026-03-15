package io.konifer.common.http

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    val message: String?,
)
