package io.konifer.domain.context

import io.konifer.common.image.ManipulationParameters.VARIANT_PROFILE
import io.konifer.common.selector.ReturnFormat
import io.konifer.domain.context.selector.QuerySelectors
import io.konifer.domain.ports.VariantProfileRepository
import io.konifer.domain.transformation.RequestedTransformation
import io.konifer.domain.transformation.Transformation
import io.konifer.domain.transformation.TransformationNormalizer
import io.konifer.domain.transformation.TransformationValidator
import io.konifer.domain.variant.OnDemandVariantMode
import io.konifer.domain.variant.attribute.Attributes

class RequestContextValidator(
    private val variantProfileRepository: VariantProfileRepository,
    private val transformationNormalizer: TransformationNormalizer,
) {
    fun validateFetchRequest(
        querySelectors: QuerySelectors,
        requestedTransformation: RequestedTransformation?,
    ) {
        if (querySelectors.returnFormat in setOf(ReturnFormat.INFO, ReturnFormat.ENTRIES) && requestedTransformation != null) {
            throw InvalidPathException("Cannot specify image attributes when requesting asset metadata")
        }
    }

    fun validateDelivery(
        context: QueryRequestContext,
        originalAttributes: Attributes,
        transformation: Transformation,
    ) {
        // Reading unchanged original bytes must not be subject to generation policies or limits.
        if (transformation == Transformation.fromAttributes(originalAttributes)) return
        TransformationValidator.validateNormalizedTransformation(context.pathConfiguration.transform.limits, transformation)
        when (context.pathConfiguration.transform.onDemandVariant.mode) {
            OnDemandVariantMode.PROFILE_ONLY -> {
                val profileName =
                    context.request.parameters[VARIANT_PROFILE]
                        ?: throw IllegalRequestedTransformationException("A variant profile is required")
                val profile = variantProfileRepository.fetch(profileName)
                val expected =
                    transformationNormalizer.resolve(
                        profile.copy(format = profile.format ?: transformation.format),
                        originalAttributes,
                    )
                if (!transformation.hasSameRenderingSemantics(expected)) {
                    throw IllegalRequestedTransformationException("Transformation must match the selected profile")
                }
            }

            OnDemandVariantMode.DISABLED -> {
                val allowed =
                    context.pathConfiguration.transform.eagerVariants.any { name ->
                        transformation.hasSameRenderingSemantics(
                            transformationNormalizer.resolve(variantProfileRepository.fetch(name), originalAttributes),
                        )
                    }
                if (!allowed) throw IllegalRequestedTransformationException("Transformation not allowed")
            }

            OnDemandVariantMode.ENABLED -> {}
        }
    }

    private fun Transformation.hasSameRenderingSemantics(other: Transformation): Boolean =
        this == other && isAutoRotate == other.isAutoRotate &&
            isColorSpaceLocked == other.isColorSpaceLocked && canUpscale == other.canUpscale
}
