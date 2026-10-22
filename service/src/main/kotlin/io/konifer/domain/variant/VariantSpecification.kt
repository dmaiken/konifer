package io.konifer.domain.variant

import io.konifer.domain.transformation.Transformation
import io.konifer.domain.variant.attribute.Attributes

/** Selects stored original content or content matching a normalized transformation. */
sealed interface VariantSpecification {
    fun matches(
        stored: VariantSpecification,
        attributes: Attributes,
    ): Boolean =
        when (this) {
            Original -> {
                stored == Original
            }

            is Transformed -> {
                transformation ==
                    when (stored) {
                        Original -> Transformation.fromAttributes(attributes)
                        is Transformed -> stored.transformation
                    }
            }
        }

    /** The stored baseline, including any preprocessing applied when the asset was ingested. */
    data object Original : VariantSpecification

    /** May also match the original when its stored attributes describe this transformation. */
    data class Transformed(
        val transformation: Transformation,
    ) : VariantSpecification
}
