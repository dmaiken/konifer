package io.konifer.domain.variant

sealed interface VariantGenerationResult {
    data class Generated(
        val variant: VariantData,
    ) : VariantGenerationResult

    data object AlreadyExists : VariantGenerationResult
}
