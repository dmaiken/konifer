package io.asset.context

import image.model.RequestedImageAttributes
import io.asset.ManipulationParameters.HEIGHT
import io.asset.ManipulationParameters.MIME_TYPE
import io.asset.ManipulationParameters.WIDTH
import io.ktor.http.Parameters
import io.ktor.util.logging.KtorSimpleLogger
import io.path.DeleteMode
import io.path.configuration.PathConfigurationService
import io.properties.validateAndCreate

class RequestContextFactory(
    private val pathConfigurationService: PathConfigurationService,
) {
    companion object {
        const val PATH_NAMESPACE_SEPARATOR = "-"
        const val ASSET_PATH_PREFIX = "/assets"
        const val ENTRY_ID_MODIFIER = "ENTRY"
    }

    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    fun fromGetRequest(
        path: String,
        queryParameters: Parameters,
    ): QueryRequestContext {
        val segments = extractPathSegments(path)
        val queryModifiers = extractQueryModifiers(segments.getOrNull(1))
        val requestedImageAttributes = extractRequestedImageAttributes(queryParameters)
        if (queryModifiers.returnFormat == ReturnFormat.METADATA && requestedImageAttributes != null) {
            throw InvalidPathException("Cannot specify image attributes when requesting asset metadata")
        }

        return QueryRequestContext(
            path = segments.first(),
            pathConfiguration = pathConfigurationService.fetchConfigurationForPath(segments[0]),
            modifiers = queryModifiers,
            requestedImageAttributes = requestedImageAttributes,
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
        val segments = route.trim('/').split("$PATH_NAMESPACE_SEPARATOR/")
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

    private fun extractRequestedImageAttributes(parameters: Parameters): RequestedImageAttributes? {
        return if (parameters[WIDTH] == null && parameters[HEIGHT] == null && parameters[MIME_TYPE] == null) {
            null
        } else {
            validateAndCreate {
                RequestedImageAttributes(
                    width = parameters[WIDTH]?.toIntOrNull(),
                    height = parameters[HEIGHT]?.toIntOrNull(),
                    mimeType = parameters[MIME_TYPE],
                )
            }
        }
    }
}
