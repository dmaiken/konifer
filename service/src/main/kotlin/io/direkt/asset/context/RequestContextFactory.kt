package io.direkt.asset.context

import io.direkt.asset.ManipulationParameters.ALL_RESERVED_PARAMETERS
import io.direkt.asset.ManipulationParameters.ALL_TRANSFORMATION_PARAMETERS
import io.direkt.asset.ManipulationParameters.BACKGROUND
import io.direkt.asset.ManipulationParameters.BLUR
import io.direkt.asset.ManipulationParameters.FILTER
import io.direkt.asset.ManipulationParameters.FIT
import io.direkt.asset.ManipulationParameters.FLIP
import io.direkt.asset.ManipulationParameters.GRAVITY
import io.direkt.asset.ManipulationParameters.HEIGHT
import io.direkt.asset.ManipulationParameters.MIME_TYPE
import io.direkt.asset.ManipulationParameters.PAD
import io.direkt.asset.ManipulationParameters.QUALITY
import io.direkt.asset.ManipulationParameters.ROTATE
import io.direkt.asset.ManipulationParameters.VARIANT_PROFILE
import io.direkt.asset.ManipulationParameters.WIDTH
import io.direkt.service.TransformationNormalizer
import io.direkt.asset.variant.VariantProfileRepository
import io.direkt.image.model.Filter
import io.direkt.image.model.Fit
import io.direkt.image.model.Flip
import io.direkt.image.model.Gravity
import io.direkt.image.model.ImageFormat
import io.direkt.image.model.RequestedTransformation
import io.direkt.image.model.Rotate
import io.direkt.path.DeleteMode
import io.direkt.path.configuration.PathConfigurationRepository
import io.direkt.properties.validateAndCreate
import io.ktor.http.Parameters
import io.ktor.util.logging.KtorSimpleLogger

class RequestContextFactory(
    private val pathConfigurationRepository: PathConfigurationRepository,
    private val variantProfileRepository: VariantProfileRepository,
    private val transformationNormalizer: TransformationNormalizer,
) {
    companion object {
        const val PATH_NAMESPACE_SEPARATOR = "-"
        const val ASSET_PATH_PREFIX = "/assets"
        const val ENTRY_ID_MODIFIER = "ENTRY"
    }

    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

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

    fun fromDeleteRequest(path: String): DeleteRequestContext {
        val segments = extractPathSegments(path)

        return DeleteRequestContext(
            path = segments.first(),
            modifiers = extractDeleteModifiers(segments.getOrNull(1)),
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

    private fun extractDeleteModifiers(path: String?): DeleteModifiers {
        if (path == null || path.isBlank()) {
            return DeleteModifiers()
        }
        val deleteModifierSegments = path.trim('/').uppercase().split('/')
        if (deleteModifierSegments.size > 2) {
            throw InvalidDeleteModifiersException("Too many delete modifiers: $deleteModifierSegments")
        }

        val deleteModifiers =
            try {
                when (deleteModifierSegments.size) {
                    2 -> {
                        if (deleteModifierSegments[0] != ENTRY_ID_MODIFIER) {
                            throw InvalidDeleteModifiersException("Invalid delete modifiers: $deleteModifierSegments")
                        }
                        DeleteModifiers(
                            entryId = deleteModifierSegments[1].toNonNegativeLong(),
                        )
                    }
                    1 ->
                        DeleteModifiers(
                            mode = DeleteMode.valueOf(deleteModifierSegments[0]),
                        )
                    else -> DeleteModifiers()
                }
            } catch (e: Exception) {
                throw InvalidDeleteModifiersException("Invalid delete modifiers: $deleteModifierSegments", e)
            }
        if (deleteModifiers.entryId != null && deleteModifiers.mode != DeleteMode.SINGLE) {
            throw InvalidDeleteModifiersException("Cannot supply an entryId and mode of: ${deleteModifiers.mode}")
        }
        logger.info("Parsed delete modifiers for path: $path - $deleteModifiers")

        return deleteModifiers
    }

    private fun extractQueryModifiers(path: String?): QueryModifiers {
        if (path == null || path.isBlank()) {
            return QueryModifiers()
        }
        val queryModifierSegments = path.trim('/').uppercase().split('/')
        if (queryModifierSegments.size > 3) {
            throw InvalidQueryModifiersException("Too many query modifiers: $queryModifierSegments")
        }

        val queryModifiers =
            try {
                when (queryModifierSegments.size) {
                    3 -> {
                        if (queryModifierSegments[1] == ENTRY_ID_MODIFIER) {
                            QueryModifiers(
                                returnFormat = ReturnFormat.valueOf(queryModifierSegments[0]),
                                entryId = queryModifierSegments[2].toNonNegativeLong(),
                                specifiedModifiers =
                                    SpecifiedInRequest(
                                        returnFormat = true,
                                    ),
                            )
                        } else {
                            QueryModifiers(
                                returnFormat = ReturnFormat.valueOf(queryModifierSegments[0]),
                                orderBy = OrderBy.valueOf(queryModifierSegments[1]),
                                limit = queryModifierSegments[2].toPositiveInt(),
                                specifiedModifiers =
                                    SpecifiedInRequest(
                                        returnFormat = true,
                                        orderBy = true,
                                        limit = true,
                                    ),
                            )
                        }
                    }
                    2 -> {
                        if (OrderBy.valueOfOrNull(queryModifierSegments[0]) != null) {
                            QueryModifiers(
                                orderBy = OrderBy.valueOf(queryModifierSegments[0]),
                                limit = queryModifierSegments[1].toPositiveInt(),
                                specifiedModifiers =
                                    SpecifiedInRequest(
                                        orderBy = true,
                                        limit = true,
                                    ),
                            )
                        } else if (ReturnFormat.valueOfOrNull(queryModifierSegments[0]) != null) {
                            val secondInt = queryModifierSegments[1].toIntOrNull()
                            if (secondInt != null) {
                                QueryModifiers(
                                    returnFormat = ReturnFormat.valueOf(queryModifierSegments[0]),
                                    limit = queryModifierSegments[1].toPositiveInt(),
                                    specifiedModifiers =
                                        SpecifiedInRequest(
                                            returnFormat = true,
                                            limit = true,
                                        ),
                                )
                            } else {
                                QueryModifiers(
                                    returnFormat = ReturnFormat.valueOf(queryModifierSegments[0]),
                                    orderBy = OrderBy.valueOf(queryModifierSegments[1]),
                                    specifiedModifiers =
                                        SpecifiedInRequest(
                                            returnFormat = true,
                                            orderBy = true,
                                        ),
                                )
                            }
                        } else if (queryModifierSegments[0] == ENTRY_ID_MODIFIER) {
                            QueryModifiers(
                                entryId = queryModifierSegments[1].toNonNegativeLong(),
                            )
                        } else {
                            throw InvalidQueryModifiersException("Invalid query modifiers: $queryModifierSegments")
                        }
                    }
                    1 ->
                        if (queryModifierSegments[0].toIntOrNull() != null) {
                            QueryModifiers(
                                limit = queryModifierSegments[0].toPositiveInt(),
                                specifiedModifiers =
                                    SpecifiedInRequest(
                                        limit = true,
                                    ),
                            )
                        } else if (ReturnFormat.valueOfOrNull(queryModifierSegments[0]) != null) {
                            QueryModifiers(
                                returnFormat = ReturnFormat.valueOf(queryModifierSegments[0]),
                                specifiedModifiers =
                                    SpecifiedInRequest(
                                        returnFormat = true,
                                    ),
                            )
                        } else if (OrderBy.valueOfOrNull(queryModifierSegments[0]) != null) {
                            QueryModifiers(
                                orderBy = OrderBy.valueOf(queryModifierSegments[0]),
                                specifiedModifiers =
                                    SpecifiedInRequest(
                                        orderBy = true,
                                    ),
                            )
                        } else {
                            throw IllegalArgumentException("Invalid query modifiers: $queryModifierSegments")
                        }
                    else -> QueryModifiers() // Defaults
                }
            } catch (e: Exception) {
                throw InvalidQueryModifiersException("Invalid query modifiers: $queryModifierSegments", e)
            }

        if ((queryModifiers.returnFormat == ReturnFormat.CONTENT || queryModifiers.returnFormat == ReturnFormat.REDIRECT) &&
            queryModifiers.limit > 1
        ) {
            throw InvalidQueryModifiersException(
                "Cannot have limit > 1 with return format of: ${queryModifiers.returnFormat.name.lowercase()}",
            )
        }
        logger.info("Parsed query modifiers for path: $path - $queryModifiers")

        return queryModifiers
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
            validateAndCreate {
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
            .also {
                logger.info("Extracted labels: $it")
            }
}
