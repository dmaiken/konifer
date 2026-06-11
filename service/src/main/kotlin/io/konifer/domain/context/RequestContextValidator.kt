package io.konifer.domain.context

import io.konifer.common.image.ManipulationParameters
import io.konifer.common.image.ManipulationParameters.ALL_TRANSFORMATION_PARAMETERS
import io.konifer.common.selector.ReturnFormat
import io.konifer.domain.context.selector.QuerySelectors
import io.konifer.domain.path.PathConfiguration
import io.konifer.domain.ports.VariantProfileRepository
import io.konifer.domain.variant.OnDemandVariantMode
import io.ktor.http.Parameters

class RequestContextValidator(
    private val variantProfileRepository: VariantProfileRepository,
) {
    fun validateFetchRequest(
        pathConfiguration: PathConfiguration,
        querySelectors: QuerySelectors,
        requestedTransformation: RequestedTransformation?,
        queryParameters: Parameters,
    ) {
        when (querySelectors.returnFormat) {
            ReturnFormat.INFO -> {
                if (
                    requestedTransformation != null &&
                    !requestedTransformation.originalVariant
                ) {
                    throw InvalidPathException("Cannot specify image attributes when requesting asset metadata")
                }
            }
            ReturnFormat.LINK, ReturnFormat.CONTENT, ReturnFormat.REDIRECT, ReturnFormat.DOWNLOAD -> {
                if (requestedTransformation == null || requestedTransformation == RequestedTransformation.ORIGINAL_VARIANT) return
                validateOnDemandVariantMode(
                    pathConfiguration = pathConfiguration,
                    requestedTransformation = requestedTransformation,
                    queryParameters = queryParameters,
                )
            }
        }
    }

    private fun validateOnDemandVariantMode(
        pathConfiguration: PathConfiguration,
        requestedTransformation: RequestedTransformation,
        queryParameters: Parameters,
    ) {
        when (pathConfiguration.transform.onDemandVariant.mode) {
            OnDemandVariantMode.PROFILE_ONLY -> {
                if (ALL_TRANSFORMATION_PARAMETERS.any { it.lowercase() in queryParameters }) {
                    throw IllegalRequestedTransformationException("Only '${ManipulationParameters.VARIANT_PROFILE}' can be specified")
                }
            }
            OnDemandVariantMode.DISABLED -> {
                val allowedTransformations =
                    pathConfiguration.transform.eagerVariants.map { profileName ->
                        variantProfileRepository.fetch(profileName)
                    }
                // Only eager variants allowed since on-demand variants are disabled
                if (requestedTransformation !in allowedTransformations) {
                    throw IllegalRequestedTransformationException("Transformation not allowed")
                }
            }
            OnDemandVariantMode.ENABLED -> {}
        }
    }
}
