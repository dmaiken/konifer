package io.konifer.domain.variant

import io.konifer.domain.variant.projection.VariantDeliveryProjection

sealed interface VariantGenerationResult {
    data class Generated(
        val variant: VariantDeliveryProjection,
    ) : VariantGenerationResult

    data object AlreadyExists : VariantGenerationResult
}
