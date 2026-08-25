package io.konifer.domain.transformation

class ConfiguredTransformationValidationException(
    override val message: String?,
    override val cause: Throwable?,
) : RuntimeException(message, cause)
