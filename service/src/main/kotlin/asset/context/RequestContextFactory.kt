package io.asset.context

import image.model.ImageFormat
import image.model.RequestedImageTransformation
import io.asset.ManipulationParameters.ALL_PARAMETERS
import io.asset.ManipulationParameters.BLUR
import io.asset.ManipulationParameters.FILTER
import io.asset.ManipulationParameters.FIT
import io.asset.ManipulationParameters.FLIP
import io.asset.ManipulationParameters.GRAVITY
import io.asset.ManipulationParameters.HEIGHT
import io.asset.ManipulationParameters.MIME_TYPE
import io.asset.ManipulationParameters.QUALITY
import io.asset.ManipulationParameters.ROTATE
import io.asset.ManipulationParameters.VARIANT_PROFILE
import io.asset.ManipulationParameters.WIDTH
import io.asset.handler.RequestedTransformationNormalizer
import io.asset.variant.VariantProfileRepository
import io.image.model.Filter
import io.image.model.Fit
import io.image.model.Flip
import io.image.model.Gravity
import io.image.model.Rotate
import io.ktor.http.Parameters
import io.ktor.util.logging.KtorSimpleLogger
import io.path.DeleteMode
import io.path.configuration.PathConfigurationRepository
import io.properties.validateAndCreate

class RequestContextFactory(
    private val pathConfigurationRepository: PathConfigurationRepository,
    private val variantProfileRepository: VariantProfileRepository,
    private val requestedTransformationNormalizer: RequestedTransformationNormalizer,
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
        val requestedImageAttributes = extractRequestedImageAttributes(queryModifiers, queryParameters)
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
                    requestedTransformationNormalizer.normalize(
                        treePath = segments.first(),
                        entryId = queryModifiers.entryId,
                        requested = it,
                    )
                },
        )
    }

    fun fromDeleteRequest(path: String): DeleteRequestContext {
        val segments = extractPathSegments(path)

        return DeleteRequestContext(
            path = segments.first(),
            modifiers = extractDeleteModifiers(segments.getOrNull(1)),
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
                            )
                        } else {
                            QueryModifiers(
                                returnFormat = ReturnFormat.valueOf(queryModifierSegments[0]),
                                orderBy = OrderBy.valueOf(queryModifierSegments[1]),
                                limit = queryModifierSegments[2].toPositiveInt(),
                            )
                        }
                    }
                    2 -> {
                        if (OrderBy.valueOfOrNull(queryModifierSegments[0]) != null) {
                            QueryModifiers(
                                orderBy = OrderBy.valueOf(queryModifierSegments[0]),
                                limit = queryModifierSegments[1].toPositiveInt(),
                            )
                        } else if (ReturnFormat.valueOfOrNull(queryModifierSegments[0]) != null) {
                            val secondInt = queryModifierSegments[1].toIntOrNull()
                            if (secondInt != null) {
                                QueryModifiers(
                                    returnFormat = ReturnFormat.valueOf(queryModifierSegments[0]),
                                    limit = queryModifierSegments[1].toPositiveInt(),
                                )
                            } else {
                                QueryModifiers(
                                    returnFormat = ReturnFormat.valueOf(queryModifierSegments[0]),
                                    orderBy = OrderBy.valueOf(queryModifierSegments[1]),
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
                            )
                        } else if (ReturnFormat.valueOfOrNull(queryModifierSegments[0]) != null) {
                            QueryModifiers(
                                returnFormat = ReturnFormat.valueOf(queryModifierSegments[0]),
                            )
                        } else if (OrderBy.valueOfOrNull(queryModifierSegments[0]) != null) {
                            QueryModifiers(
                                orderBy = OrderBy.valueOf(queryModifierSegments[0]),
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

    private fun extractRequestedImageAttributes(
        queryModifiers: QueryModifiers,
        parameters: Parameters,
    ): RequestedImageTransformation? {
        val variantProfile =
            parameters[VARIANT_PROFILE]?.let { profileName ->
                variantProfileRepository.fetch(profileName)
            }
        return if (queryModifiers.returnFormat == ReturnFormat.METADATA && variantProfile == null &&
            ALL_PARAMETERS.none {
                parameters.contains(it)
            }
        ) {
            null
        } else if (variantProfile == null && ALL_PARAMETERS.none { parameters.contains(it) }) {
            RequestedImageTransformation.ORIGINAL_VARIANT
        } else {
            validateAndCreate {
                RequestedImageTransformation(
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
                )
            }
        }
    }
}
