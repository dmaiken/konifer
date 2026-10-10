package io.konifer.domain.variant

import io.konifer.domain.transformation.Transformation

/** Selects stored original content or content matching a normalized transformation. */
sealed interface VariantSpecification {
    /** The stored baseline, including any preprocessing applied when the asset was ingested. */
    data object Original : VariantSpecification

    /** May also match the original when its stored attributes describe this transformation. */
    data class Transformed(
        val transformation: Transformation,
    ) : VariantSpecification
}
