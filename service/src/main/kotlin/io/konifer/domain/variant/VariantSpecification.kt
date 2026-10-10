package io.konifer.domain.variant

import io.konifer.domain.transformation.Transformation

sealed interface VariantSpecification {

    data object Original : VariantSpecification

    data class Transformed(
        val transformation: Transformation,
    ) : VariantSpecification
}
