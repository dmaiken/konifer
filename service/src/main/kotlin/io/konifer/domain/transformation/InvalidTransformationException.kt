package io.konifer.domain.transformation

class InvalidTransformationException(
    override val message: String?,
    override val cause: Throwable? = null,
) : RuntimeException(message, cause)
