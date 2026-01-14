package io.direkt.service.context

import io.direkt.domain.image.Filter
import io.direkt.domain.image.Fit
import io.direkt.domain.image.Flip
import io.direkt.domain.image.Gravity
import io.direkt.domain.image.ImageFormat
import io.direkt.domain.image.Rotate
import io.direkt.domain.ports.PathConfigurationRepository
import io.direkt.domain.ports.VariantProfileRepository
import io.direkt.service.context.PathModifierExtractor.extractDeleteModifiers
import io.direkt.service.context.PathModifierExtractor.extractQueryModifiers
import io.direkt.service.context.modifiers.ManipulationParameters.ALL_RESERVED_PARAMETERS
import io.direkt.service.context.modifiers.ManipulationParameters.ALL_TRANSFORMATION_PARAMETERS
import io.direkt.service.context.modifiers.ManipulationParameters.BACKGROUND
import io.direkt.service.context.modifiers.ManipulationParameters.BLUR
import io.direkt.service.context.modifiers.ManipulationParameters.FILTER
import io.direkt.service.context.modifiers.ManipulationParameters.FIT
import io.direkt.service.context.modifiers.ManipulationParameters.FLIP
import io.direkt.service.context.modifiers.ManipulationParameters.GRAVITY
import io.direkt.service.context.modifiers.ManipulationParameters.HEIGHT
import io.direkt.service.context.modifiers.ManipulationParameters.MIME_TYPE
import io.direkt.service.context.modifiers.ManipulationParameters.PAD
import io.direkt.service.context.modifiers.ManipulationParameters.QUALITY
import io.direkt.service.context.modifiers.ManipulationParameters.ROTATE
import io.direkt.service.context.modifiers.ManipulationParameters.VARIANT_PROFILE
import io.direkt.service.context.modifiers.ManipulationParameters.WIDTH
import io.direkt.service.context.modifiers.QueryModifiers
import io.direkt.service.context.modifiers.ReturnFormat
import io.direkt.service.transformation.TransformationNormalizer
import io.ktor.http.Parameters

class RequestContextFactory(
    private val pathConfigurationRepository: PathConfigurationRepository,
    private val variantProfileRepository: VariantProfileRepository,
    private val transformationNormalizer: TransformationNormalizer,
) {
    companion object {
        const val PATH_NAMESPACE_SEPARATOR = "-"
        const val ASSET_PATH_PREFIX = "/assets"
        const val ENTRY_ID_MODIFIER = "ENTRY"
        const val NO_LIMIT_MODIFIER = "ALL"
        const val RECURSIVE_MODIFIER = "RECURSIVE"
    }

    fun fromStoreRequest(
        path: String,
        mimeType: String,
    ): StoreRequestContext {
        if (extractPathSegments(path).size > 1) {
            throw InvalidPathException("Store request cannot have modifiers in path: $path")
        }
        val route = extractRoute(path)
        val pathConfiguration = pathConfigurationRepository.fetch(route)
        pathConfiguration.allowedContentTypes?.let {
            if (!it.contains(mimeType)) {
                throw ContentTypeNotPermittedException("Content type: $mimeType not permitted")
            }
        }

        return StoreRequestContext(
            path = route,
            pathConfiguration = pathConfiguration,
        )
    }

    suspend fun fromGetRequest(
        path: String,
        queryParameters: Parameters,
    ): QueryRequestContext {
        val segments = extractPathSegments(path)
        val queryModifiers = extractQueryModifiers(segments.getOrNull(1))
        val requestedImageAttributes = extractRequestedImageTransformation(queryModifiers, queryParameters)
        if (
            queryModifiers.returnFormat == ReturnFormat.METADATA &&
            requestedImageAttributes != null &&
            !requestedImageAttributes.originalVariant
        ) {
            throw InvalidPathException("Cannot specify image attributes when requesting asset metadata")
        }

        return QueryRequestContext(
            path = segments.first(),
            pathConfiguration = pathConfigurationRepository.fetch(segments[0]),
            modifiers = queryModifiers,
            transformation =
                requestedImageAttributes?.let {
                    transformationNormalizer.normalize(
                        treePath = segments.first(),
                        entryId = queryModifiers.entryId,
                        requested = it,
                    )
                },
            labels = extractLabels(queryParameters),
        )
    }

    fun fromDeleteRequest(
        path: String,
        queryParameters: Parameters,
    ): DeleteRequestContext {
        val segments = extractPathSegments(path)

        return DeleteRequestContext(
            path = segments.first(),
            modifiers = extractDeleteModifiers(segments.getOrNull(1)),
            labels = extractLabels(queryParameters),
        )
    }

    fun fromUpdateRequest(path: String): UpdateRequestContext {
        val segments = extractPathSegments(path)
        val modifiers = extractQueryModifiers(segments.getOrNull(1))
        if (modifiers.entryId == null) {
            throw InvalidPathException("Entry id must be specified on an update request")
        }
        if (modifiers.specifiedModifiers.limit) {
            throw InvalidPathException("Limit cannot be supplied on update request")
        }
        if (modifiers.specifiedModifiers.returnFormat) {
            throw InvalidPathException("Return format cannot be supplied on update request")
        }
        if (modifiers.specifiedModifiers.orderBy) {
            throw InvalidPathException("Order cannot be supplied on update request")
        }
        return UpdateRequestContext(
            path = segments.first(),
            entryId = modifiers.entryId,
        )
    }

    private fun extractPathSegments(path: String): List<String> {
        val route = extractRoute(path)
        val segments = route.removeSuffix("/").split("$PATH_NAMESPACE_SEPARATOR/")
        if (segments.size > 2) {
            throw InvalidPathException("$path has more than one '$PATH_NAMESPACE_SEPARATOR' segment")
        }

        return segments
    }

    private fun extractRoute(path: String): String {
        if (!path.startsWith(ASSET_PATH_PREFIX)) {
            throw InvalidPathException("Asset path must start with: $ASSET_PATH_PREFIX")
        }
        return path.removePrefix(ASSET_PATH_PREFIX)
    }

    private fun extractRequestedImageTransformation(
        queryModifiers: QueryModifiers,
        parameters: Parameters,
    ): RequestedTransformation? {
        val variantProfile =
            parameters[VARIANT_PROFILE]?.let { profileName ->
                variantProfileRepository.fetch(profileName)
            }
        return if (queryModifiers.returnFormat == ReturnFormat.METADATA &&
            variantProfile == null &&
            ALL_TRANSFORMATION_PARAMETERS.none {
                parameters.contains(it)
            }
        ) {
            null
        } else if (variantProfile == null && ALL_TRANSFORMATION_PARAMETERS.none { parameters.contains(it) }) {
            RequestedTransformation.ORIGINAL_VARIANT
        } else {
            RequestedTransformation(
                width = parameters[WIDTH]?.toInt() ?: variantProfile?.width,
                height = parameters[HEIGHT]?.toInt() ?: variantProfile?.height,
                format = parameters[MIME_TYPE]?.let { ImageFormat.fromMimeType(it) } ?: variantProfile?.format,
                fit = Fit.fromQueryParameters(parameters, FIT) ?: variantProfile?.fit ?: Fit.default,
                gravity = Gravity.fromQueryParameters(parameters, GRAVITY) ?: variantProfile?.gravity ?: Gravity.default,
                rotate = Rotate.fromQueryParameters(parameters, ROTATE) ?: variantProfile?.rotate ?: Rotate.default,
                flip = Flip.fromQueryParameters(parameters, FLIP) ?: variantProfile?.flip ?: Flip.default,
                filter = Filter.fromQueryParameters(parameters, FILTER) ?: variantProfile?.filter ?: Filter.default,
                blur = parameters[BLUR]?.toInt() ?: variantProfile?.blur,
                quality = parameters[QUALITY]?.toInt() ?: variantProfile?.quality,
                pad = parameters[PAD]?.toInt() ?: variantProfile?.pad,
                background = parameters[BACKGROUND] ?: variantProfile?.background,
            )
        }
    }

    /**
     * Extract any labels supplied to filter against. Check query parameters for:
     * 1. Namespaced labels - labels starting with "label:"
     * 2. Check for any query parameter not already reserved as a currently used param (e.g. bg, h, w, etc)
     */
    private fun extractLabels(parameters: Parameters): Map<String, String> =
        parameters
            .entries()
            .filter { it.value.isNotEmpty() }
            .filter { !ALL_RESERVED_PARAMETERS.contains(it.key) }
            .map { Pair(it.key.substringAfter("label:"), it.value) }
            .associate { it.first to it.second.first() }
}
