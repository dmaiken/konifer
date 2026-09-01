package io.konifer.domain.transformation

import io.konifer.domain.ports.VariantProfileRepository
import io.konifer.domain.variant.TransformProperties
import io.konifer.domain.variant.TransformationLimitProperties

class TransformConfigurationValidator(
    private val variantProfileRepository: VariantProfileRepository,
) {
    fun validate(transformProperties: TransformProperties) {
        validateEagerVariants(
            eagerVariants = transformProperties.eagerVariants,
            limits = transformProperties.limits,
        )
        validatePreprocessing(
            preProcessing = transformProperties.preProcessing,
            limits = transformProperties.limits,
        )
    }

    private fun validateEagerVariants(
        eagerVariants: List<String>,
        limits: TransformationLimitProperties,
    ) {
        eagerVariants.forEach { profileName ->
            val requested = variantProfileRepository.fetch(profileName)

            try {
                TransformationValidator.validateRequestedTransformation(
                    limits = limits,
                    requested = requested,
                )
            } catch (e: InvalidTransformationException) {
                throw ConfiguredTransformationValidationException("Eager variant '$profileName' validation failed", e)
            }
        }
    }

    private fun validatePreprocessing(
        preProcessing: PreProcessingProperties,
        limits: TransformationLimitProperties,
    ) {
        if (preProcessing.enabled) {
            try {
                TransformationValidator.validateRequestedTransformation(
                    limits = limits,
                    requested = preProcessing.requestedImageTransformation,
                )
            } catch (e: InvalidTransformationException) {
                throw ConfiguredTransformationValidationException("Preprocessing validation failed", e)
            }
        }
    }
}
