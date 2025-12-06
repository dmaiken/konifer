package io.direkt.domain.ports

import io.direkt.domain.image.RequestedTransformation

interface VariantProfileRepository {

    fun fetch(profileName: String): RequestedTransformation
}