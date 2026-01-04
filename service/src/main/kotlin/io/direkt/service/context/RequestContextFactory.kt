package io.direkt.service.context

import io.direkt.domain.image.Filter
import io.direkt.domain.image.Fit
import io.direkt.domain.image.Flip
import io.direkt.domain.image.Gravity
import io.direkt.domain.image.ImageFormat
import io.direkt.domain.image.Rotate
import io.direkt.domain.ports.PathConfigurationRepository
import io.direkt.domain.ports.VariantProfileRepository
import io.direkt.infrastructure.properties.validateAndCreate
import io.direkt.service.context.ManipulationParameters.ALL_RESERVED_PARAMETERS
import io.direkt.service.context.ManipulationParameters.ALL_TRANSFORMATION_PARAMETERS
import io.direkt.service.context.ManipulationParameters.BACKGROUND
import io.direkt.service.context.ManipulationParameters.BLUR
import io.direkt.service.context.ManipulationParameters.FILTER
import io.direkt.service.context.ManipulationParameters.FIT
import io.direkt.service.context.ManipulationParameters.FLIP
import io.direkt.service.context.ManipulationParameters.GRAVITY
import io.direkt.service.context.ManipulationParameters.HEIGHT
import io.direkt.service.context.ManipulationParameters.MIME_TYPE
import io.direkt.service.context.ManipulationParameters.PAD
import io.direkt.service.context.ManipulationParameters.QUALITY
import io.direkt.service.context.ManipulationParameters.ROTATE
import io.direkt.service.context.ManipulationParameters.VARIANT_PROFILE
import io.direkt.service.context.ManipulationParameters.WIDTH
import io.direkt.service.transformation.TransformationNormalizer
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
        const val NO_LIMIT_MODIFIER = "ALL"
        const val RECURSIVE_MODIFIER = "RECURSIVE"
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
    ): AssetQueryRequestContext {
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

        return AssetQueryRequestContext(
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

    private fun extractDeleteModifiers(path: String?): DeleteModifiers {
        if (path.isNullOrBlank()) {
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
                        if (deleteModifierSegments[0] == ENTRY_ID_MODIFIER) {
                            DeleteModifiers(
                                entryId = deleteModifierSegments[1].toNonNegativeLong(),
                            )
                        } else {
                            DeleteModifiers(
                                orderBy = OrderBy.valueOf(deleteModifierSegments[0]),
                                limit = toLimit(deleteModifierSegments[1]),
                            )
                        }
                    }
                    1 -> {
                        if (deleteModifierSegments[0] == RECURSIVE_MODIFIER) {
                            DeleteModifiers(
                                recursive = true,
                            )
                        } else if (OrderBy.valueOfOrNull(deleteModifierSegments[0]) != null) {
                            DeleteModifiers(
                                orderBy = OrderBy.valueOf(deleteModifierSegments[0]),
                            )
                        } else if (deleteModifierSegments[0] == NO_LIMIT_MODIFIER || deleteModifierSegments[0].toIntOrNull() != null) {
                            DeleteModifiers(
                                limit = toLimit(deleteModifierSegments[0]),
                            )
                        } else {
                            throw InvalidDeleteModifiersException("Invalid delete modifiers: $deleteModifierSegments")
                        }
                    }
                    else -> DeleteModifiers()
                }
            } catch (e: Exception) {
                throw InvalidDeleteModifiersException("Invalid delete modifiers: $deleteModifierSegments", e)
            }
        logger.info("Parsed delete modifiers for path: $path - $deleteModifiers")

        return deleteModifiers
    }

    private fun extractQueryModifiers(path: String?): QueryModifiers {
        if (path.isNullOrBlank()) {
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
                                limit = toLimit(queryModifierSegments[2]),
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
                                limit = toLimit(queryModifierSegments[1]),
                                specifiedModifiers =
                                    SpecifiedInRequest(
                                        orderBy = true,
                                        limit = true,
                                    ),
                            )
                        } else if (ReturnFormat.valueOfOrNull(queryModifierSegments[0]) != null) {
                            val limitSpecified =
                                queryModifierSegments[1] == NO_LIMIT_MODIFIER || queryModifierSegments[1].toIntOrNull() != null
                            if (limitSpecified) {
                                QueryModifiers(
                                    returnFormat = ReturnFormat.valueOf(queryModifierSegments[0]),
                                    limit = toLimit(queryModifierSegments[1]),
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
                        if (queryModifierSegments[0] == NO_LIMIT_MODIFIER || queryModifierSegments[0].toIntOrNull() != null) {
                            QueryModifiers(
                                limit = toLimit(queryModifierSegments[0]),
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

    private fun toLimit(segment: String): Int =
        if (segment == NO_LIMIT_MODIFIER) {
            -1
        } else {
            segment.toPositiveInt()
        }
}
