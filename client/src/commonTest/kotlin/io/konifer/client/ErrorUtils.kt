package io.konifer.client

import io.konifer.common.http.ErrorResponse

fun createErrorResponse(message: String) =
    ErrorResponse(
        message = message,
    )
