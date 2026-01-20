package io.konifer.domain.ports

import io.konifer.service.context.RequestedTransformation

interface VariantProfileRepository {
    fun fetch(profileName: String): RequestedTransformation
}
