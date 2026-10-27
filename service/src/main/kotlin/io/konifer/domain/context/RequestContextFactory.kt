package io.konifer.domain.context

import io.konifer.common.image.Filter
import io.konifer.common.image.Fit
import io.konifer.common.image.Flip
import io.konifer.common.image.Gravity
import io.konifer.common.image.ImageFormat
import io.konifer.common.image.LIMIT_PARAMETER
import io.konifer.common.image.ManipulationParameters.ALL_TRANSFORMATION_PARAMETERS
import io.konifer.common.image.ManipulationParameters.BLUR
import io.konifer.common.image.ManipulationParameters.COLOR_SPACE
import io.konifer.common.image.ManipulationParameters.FILTER
import io.konifer.common.image.ManipulationParameters.FIT
import io.konifer.common.image.ManipulationParameters.FLIP
import io.konifer.common.image.ManipulationParameters.FORMAT
import io.konifer.common.image.ManipulationParameters.GRAVITY
import io.konifer.common.image.ManipulationParameters.HEIGHT
import io.konifer.common.image.ManipulationParameters.PAD
import io.konifer.common.image.ManipulationParameters.PAD_COLOR
import io.konifer.common.image.ManipulationParameters.PIXEL_CHANNELS
import io.konifer.common.image.ManipulationParameters.PIXEL_LAYOUT
import io.konifer.common.image.ManipulationParameters.QUALITY
import io.konifer.common.image.ManipulationParameters.ROTATE
import io.konifer.common.image.ManipulationParameters.STRIP
import io.konifer.common.image.ManipulationParameters.VARIANT_PROFILE
import io.konifer.common.image.ManipulationParameters.WIDTH
import io.konifer.common.image.Rotate
import io.konifer.common.image.TransformableColorSpace
import io.konifer.common.image.pixel.PixelLayout
import io.konifer.domain.asset.AssetLabels
import io.konifer.domain.asset.toAssetLabels
import io.konifer.domain.context.PathSelectorExtractor.extractDeleteSelectors
import io.konifer.domain.context.PathSelectorExtractor.extractQuerySelectors
import io.konifer.domain.image.fromFormat
import io.konifer.domain.image.fromQueryParameters
import io.konifer.domain.ports.PathConfigurationRepository
import io.konifer.domain.ports.VariantProfileRepository
import io.konifer.domain.transformation.InvalidTransformationException
import io.konifer.domain.transformation.RequestedTransformation
import io.konifer.domain.transformation.pixel.toPixelChannels
import io.konifer.domain.transformation.toBlur
import io.konifer.domain.transformation.toDimension
import io.konifer.domain.transformation.toPaddingAmount
import io.konifer.domain.transformation.toQuality
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.RequestConnectionPoint
import io.ktor.http.parseAndSortContentTypeHeader

class RequestContextFactory(
    private val pathConfigurationRepository: PathConfigurationRepository,
    private val variantProfileRepository: VariantProfileRepository,
    private val requestContextValidator: RequestContextValidator,
) {
    companion object {
        const val PATH_NAMESPACE_SEPARATOR = "-"
        const val ASSET_PATH_PREFIX = "/assets"
        const val ENTRY_ID_MODIFIER = "ENTRY"
        const val RECURSIVE_MODIFIER = "RECURSIVE"
    }

    fun fromStoreRequest(path: String): StoreRequestContext {
        val parsedPath = parsePath(path, preserveTrailingSlash = true)
        if (parsedPath.selectorPath != null) {
            throw InvalidPathException("Store request cannot have modifiers in path: $path")
        }
        val route = parsedPath.assetPath
        val pathConfiguration = pathConfigurationRepository.fetch(route)

        return StoreRequestContext(
            path = route,
            pathConfiguration = pathConfiguration,
        )
    }

    fun fromFetchRequest(
        path: String,
        headers: Headers,
        queryParameters: Parameters,
        origin: RequestConnectionPoint,
    ): QueryRequestContext {
        val parsedPath = parsePath(path)
        val querySelectors =
            extractQuerySelectors(
                path = parsedPath.selectorPath,
                parameters = queryParameters,
            )
        val requestedTransformation =
            extractRequestedImageTransformation(
                headers = headers,
                parameters = queryParameters,
            )
        val pathConfiguration = pathConfigurationRepository.fetch(parsedPath.assetPath)
        requestContextValidator.validateFetchRequest(
            querySelectors = querySelectors,
            requestedTransformation = requestedTransformation,
        )

        return QueryRequestContext(
            path = parsedPath.assetPath,
            pathConfiguration = pathConfiguration,
            selectors = querySelectors,
            requestedTransformation = requestedTransformation,
            labels = extractLabels(queryParameters),
            request =
                HttpRequest(
                    parameters = queryParameters,
                    origin = origin,
                ),
        )
    }

    fun fromDeleteRequest(
        path: String,
        queryParameters: Parameters,
    ): DeleteRequestContext {
        val parsedPath = parsePath(path)
        val unknownParameters = queryParameters.names().filter { it != LIMIT_PARAMETER && !it.startsWith("label:") }
        if (unknownParameters.isNotEmpty()) {
            throw InvalidRequestException("Unknown delete query parameters: ${unknownParameters.sorted().joinToString()}")
        }

        return DeleteRequestContext(
            path = parsedPath.assetPath,
            selection =
                extractDeleteSelectors(
                    path = parsedPath.selectorPath,
                    parameters = queryParameters,
                ),
            labels = extractLabels(queryParameters),
        )
    }

    fun fromUpdateRequest(path: String): UpdateRequestContext {
        val parsedPath = parsePath(path)
        val selectors =
            extractQuerySelectors(
                path = parsedPath.selectorPath,
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
            path = parsedPath.assetPath,
            entryId = selectors.entryId,
        )
    }

    private data class ParsedPath(
        val assetPath: String,
        val selectorPath: String?,
    )

    private fun parsePath(
        path: String,
        preserveTrailingSlash: Boolean = false,
    ): ParsedPath {
        val rawRoute = extractRoute(path)
        val route = if (preserveTrailingSlash) rawRoute else rawRoute.removeSuffix("/")
        val segments = route.split('/')
        val separators = segments.indices.filter { segments[it] == PATH_NAMESPACE_SEPARATOR }
        if (separators.size > 1) {
            throw InvalidPathException("$path has more than one '$PATH_NAMESPACE_SEPARATOR' segment")
        }
        val separatorIndex = separators.singleOrNull() ?: return ParsedPath(route, null)
        val selectorPath = segments.drop(separatorIndex + 1).joinToString("/")
        if (selectorPath.trim('/').isBlank()) {
            throw InvalidPathException("Selector section cannot be empty: $path")
        }
        return ParsedPath(
            assetPath = segments.take(separatorIndex).joinToString("/") + "/",
            selectorPath = selectorPath,
        )
    }

    private fun extractRoute(path: String): String {
        if (path != ASSET_PATH_PREFIX && !path.startsWith("$ASSET_PATH_PREFIX/")) {
            throw InvalidPathException("Asset path must start with: $ASSET_PATH_PREFIX")
        }
        return path.removePrefix(ASSET_PATH_PREFIX)
    }

    private fun extractRequestedImageTransformation(
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
        return if (requestedOriginalVariant) {
            null
        } else {
            RequestedTransformation(
                width = parameters.integer(WIDTH)?.toDimension() ?: variantProfile?.width,
                height = parameters.integer(HEIGHT)?.toDimension() ?: variantProfile?.height,
                format = requestedFormat,
                fit = Fit.fromQueryParameters(parameters, FIT) ?: variantProfile?.fit ?: Fit.default,
                gravity =
                    Gravity.fromQueryParameters(parameters, GRAVITY) ?: variantProfile?.gravity
                        ?: Gravity.default,
                rotate = Rotate.fromQueryParameters(parameters, ROTATE) ?: variantProfile?.rotate ?: Rotate.default,
                flip = Flip.fromQueryParameters(parameters, FLIP) ?: variantProfile?.flip ?: Flip.default,
                filter = Filter.fromQueryParameters(parameters, FILTER) ?: variantProfile?.filter ?: Filter.default,
                blur = parameters.integer(BLUR)?.toBlur() ?: variantProfile?.blur,
                quality = parameters.integer(QUALITY)?.toQuality() ?: variantProfile?.quality,
                pad = parameters.integer(PAD)?.toPaddingAmount() ?: variantProfile?.pad,
                padColor = parameters[PAD_COLOR] ?: variantProfile?.padColor,
                stripMetadata = parameters[STRIP] ?: variantProfile?.stripMetadata,
                colorSpace =
                    TransformableColorSpace.fromQueryParameters(parameters, COLOR_SPACE)
                        ?: variantProfile?.colorSpace
                        ?: TransformableColorSpace.default,
                pixelChannels = parameters[PIXEL_CHANNELS]?.toPixelChannels() ?: variantProfile?.pixelChannels,
                pixelLayout = PixelLayout.fromQueryParameters(parameters, PIXEL_LAYOUT) ?: variantProfile?.pixelLayout,
            )
        }
    }

    /** Extract only explicitly namespaced label filters. */
    private fun extractLabels(parameters: Parameters): AssetLabels {
        val labels =
            parameters
                .entries()
                .filter { it.key.startsWith("label:") }
                .map { Pair(it.key.substringAfter("label:"), it.value) }
                .associate { it.first to it.second }

        if (labels.values.any { it.size != 1 }) {
            throw InvalidLabelsException("Labels must have exactly one value")
        }

        return labels.mapValues { (_, value) -> value.first() }.toAssetLabels()
    }

    /**
     * Determine the requested format in this order:
     * 1. [FORMAT] query parameter
     * 2. Format defined in variant profile (if any profile)
     * 3. Parsing the `accept` header
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

private fun Parameters.integer(name: String): Int? =
    this[name]?.let { it.toIntOrNull() ?: throw InvalidTransformationException("Invalid $name: $it") }
