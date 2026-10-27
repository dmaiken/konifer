package io.konifer.domain.context

class InvalidRequestException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
