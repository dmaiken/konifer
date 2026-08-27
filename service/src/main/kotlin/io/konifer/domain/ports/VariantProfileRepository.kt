package io.konifer.domain.ports

import io.konifer.domain.transformation.RequestedTransformation

interface VariantProfileRepository {
    fun fetch(profileName: String): RequestedTransformation
}
