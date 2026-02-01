package io.konifer.service.context

import io.konifer.domain.image.Filter
import io.konifer.domain.image.Fit
import io.konifer.domain.image.Flip
import io.konifer.domain.image.Gravity
import io.konifer.domain.image.ImageFormat
import io.konifer.domain.image.Rotate
import io.konifer.domain.ports.PathConfigurationRepository
import io.konifer.domain.ports.VariantProfileRepository
import io.konifer.service.context.PathSelectorExtractor.extractDeleteSelectors
import io.konifer.service.context.PathSelectorExtractor.extractQuerySelectors
import io.konifer.service.context.selector.ManipulationParameters.ALL_RESERVED_PARAMETERS
import io.konifer.service.context.selector.ManipulationParameters.ALL_TRANSFORMATION_PARAMETERS
import io.konifer.service.context.selector.ManipulationParameters.BACKGROUND
import io.konifer.service.context.selector.ManipulationParameters.BLUR
import io.konifer.service.context.selector.ManipulationParameters.FILTER
import io.konifer.service.context.selector.ManipulationParameters.FIT
import io.konifer.service.context.selector.ManipulationParameters.FLIP
import io.konifer.service.context.selector.ManipulationParameters.FORMAT
import io.konifer.service.context.selector.ManipulationParameters.GRAVITY
import io.konifer.service.context.selector.ManipulationParameters.HEIGHT
import io.konifer.service.context.selector.ManipulationParameters.PAD
import io.konifer.service.context.selector.ManipulationParameters.QUALITY
import io.konifer.service.context.selector.ManipulationParameters.ROTATE
import io.konifer.service.context.selector.ManipulationParameters.VARIANT_PROFILE
import io.konifer.service.context.selector.ManipulationParameters.WIDTH
import io.konifer.service.context.selector.QuerySelectors
import io.konifer.service.context.selector.ReturnFormat
import io.konifer.service.transformation.TransformationNormalizer
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.parseAndSortContentTypeHeader

class RequestContextFactory(
    private val pathConfigurationRepository: PathConfigurationRepository,
    private val variantProfileRepository: VariantProfileRepository,
    private val transformationNormalizer: TransformationNormalizer,
) {
    companion object {
        const val PATH_NAMESPACE_SEPARATOR = "-"
        const val ASSET_PATH_PREFIX = "/assets"
        const val ENTRY_ID_MODIFIER = "ENTRY"
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
        headers: Headers,
        queryParameters: Parameters,
    ): QueryRequestContext {
        val segments = extractPathSegments(path)
        val querySelectors =
            extractQuerySelectors(
                path = segments.getOrNull(1),
                parameters = queryParameters,
            )
        val requestedImageAttributes =
            extractRequestedImageTransformation(
                querySelectors = querySelectors,
                headers = headers,
                parameters = queryParameters,
            )
        if (
            querySelectors.returnFormat == ReturnFormat.METADATA &&
            requestedImageAttributes != null &&
            !requestedImageAttributes.originalVariant
        ) {
            throw InvalidPathException("Cannot specify image attributes when requesting asset metadata")
        }

        return QueryRequestContext(
            path = segments.first(),
            pathConfiguration = pathConfigurationRepository.fetch(segments[0]),
            selectors = querySelectors,
            transformation =
                requestedImageAttributes?.let {
                    transformationNormalizer.normalize(
                        treePath = segments.first(),
                        entryId = querySelectors.entryId,
                        requested = it,
                    )
                },
            labels = extractLabels(queryParameters),
            request =
                HttpRequest(
                    parameters = queryParameters,
                ),
        )
    }

    fun fromDeleteRequest(
        path: String,
        queryParameters: Parameters,
    ): DeleteRequestContext {
        val segments = extractPathSegments(path)

        return DeleteRequestContext(
            path = segments.first(),
            modifiers =
                extractDeleteSelectors(
                    path = segments.getOrNull(1),
                    parameters = queryParameters,
                ),
            labels = extractLabels(queryParameters),
        )
    }

    fun fromUpdateRequest(path: String): UpdateRequestContext {
        val segments = extractPathSegments(path)
        val selectors =
            extractQuerySelectors(
                path = segments.getOrNull(1),
                parameters = Parameters.Empty,
            )
        if (selectors.entryId == null) {
            throw InvalidPathException("Entry id must be specified on an update request")
        }
        if (selectors.specifiedModifiers.limit) {
            throw InvalidPathException("Limit cannot be supplied on update request")
        }
        if (selectors.specifiedModifiers.returnFormat) {
            throw InvalidPathException("Return format cannot be supplied on update request")
        }
        if (selectors.specifiedModifiers.orderBy) {
            throw InvalidPathException("Order cannot be supplied on update request")
        }
        return UpdateRequestContext(
            path = segments.first(),
            entryId = selectors.entryId,
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
        querySelectors: QuerySelectors,
        headers: Headers,
        parameters: Parameters,
    ): RequestedTransformation? {
        val variantProfile =
            parameters[VARIANT_PROFILE]?.let { profileName ->
                variantProfileRepository.fetch(profileName)
            }
        val requestedFormat =
            determineRequestedFormat(
                headers = headers,
                variantProfile = variantProfile,
                parameters = parameters,
            )
        val requestedOriginalVariant =
            requestedFormat == null &&
                ALL_TRANSFORMATION_PARAMETERS.none {
                    parameters.contains(it)
                } &&
                variantProfile == null
        return if (querySelectors.returnFormat == ReturnFormat.METADATA && requestedOriginalVariant) {
            null
        } else if (requestedOriginalVariant) {
            RequestedTransformation.ORIGINAL_VARIANT
        } else {
            RequestedTransformation(
                width = parameters[WIDTH]?.toInt() ?: variantProfile?.width,
                height = parameters[HEIGHT]?.toInt() ?: variantProfile?.height,
                format = requestedFormat,
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

    /**
     * Determine the requested format in this order:
     * 1. [FORMAT] query parameter
     * 2. format defined in variant profile (if any profile)
     * 3. parsing the accept header
     */
    private fun determineRequestedFormat(
        headers: Headers,
        variantProfile: RequestedTransformation?,
        parameters: Parameters,
    ): ImageFormat? {
        parameters[FORMAT]?.let {
            return ImageFormat.fromFormat(it)
        }
        variantProfile?.takeIf { it.format != null }?.let {
            return it.format
        }
        val parsedItems = parseAndSortContentTypeHeader(headers[HttpHeaders.Accept])

        return parsedItems.firstNotNullOfOrNull { contentType ->
            when (ContentType.parse(contentType.value)) {
                ContentType.Image.AVIF -> ImageFormat.AVIF
                ContentType.Image.WEBP -> ImageFormat.WEBP
                ContentType.Image.PNG -> ImageFormat.PNG
                ContentType.Image.JPEG -> ImageFormat.JPEG
                ContentType.Image.HEIC -> ImageFormat.HEIC
                ContentType.Image.GIF -> ImageFormat.GIF
                ContentType.Image.JXL -> ImageFormat.JPEG_XL
                else -> null
            }
        }
    }
}
