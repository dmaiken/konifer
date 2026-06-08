package io.konifer.domain.context

import io.konifer.common.image.ManipulationParameters
import io.konifer.common.image.ManipulationParameters.ALL_TRANSFORMATION_PARAMETERS
import io.konifer.common.selector.ReturnFormat
import io.konifer.domain.context.selector.QuerySelectors
import io.konifer.domain.path.PathConfiguration
import io.konifer.domain.variant.OnDemandVariantMode
import io.ktor.http.Parameters

object RequestContextValidator {
    fun validateGetRequest(
        pathConfiguration: PathConfiguration,
        querySelectors: QuerySelectors,
        requestedTransformation: RequestedTransformation?,
        queryParameters: Parameters,
    ) {
        when (querySelectors.returnFormat) {
            ReturnFormat.METADATA -> {
                if (
                    requestedTransformation != null &&
                    !requestedTransformation.originalVariant
                ) {
                    throw InvalidPathException("Cannot specify image attributes when requesting asset metadata")
                }
            }
            ReturnFormat.LINK, ReturnFormat.CONTENT, ReturnFormat.REDIRECT, ReturnFormat.DOWNLOAD -> {
                if (
                    pathConfiguration.transform.onDemandVariant.mode == OnDemandVariantMode.PROFILE_ONLY &&
                    ALL_TRANSFORMATION_PARAMETERS.any { it.lowercase() in queryParameters }
                ) {
                    throw IllegalRequestedTransformationException("Only '${ManipulationParameters.VARIANT_PROFILE}' can be specified")
                }
            }
        }
    }
}
