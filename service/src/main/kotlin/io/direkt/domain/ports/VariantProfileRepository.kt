package io.direkt.domain.ports

import io.direkt.service.context.RequestedTransformation

interface VariantProfileRepository {
    fun fetch(profileName: String): RequestedTransformation
}
