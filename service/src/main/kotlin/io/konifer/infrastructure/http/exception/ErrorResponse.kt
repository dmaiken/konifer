package io.konifer.infrastructure.http.exception

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    val message: String?,
)
