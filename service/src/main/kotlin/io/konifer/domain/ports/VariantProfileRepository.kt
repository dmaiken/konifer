package io.konifer.domain.ports

import io.konifer.domain.context.RequestedTransformation

interface VariantProfileRepository {
    fun fetch(profileName: String): RequestedTransformation
}
