package io.konifer.domain.variant

class VariantAlreadyExistsException(
    override val message: String,
) : RuntimeException(message)
