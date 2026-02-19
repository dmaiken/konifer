package io.konifer.domain.ports

class VariantAlreadyExistsException(
    override val message: String,
) : RuntimeException(message)
