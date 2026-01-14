package io.direkt.service.context

import io.direkt.service.context.RequestContextFactory.Companion.ENTRY_ID_MODIFIER
import io.direkt.service.context.RequestContextFactory.Companion.NO_LIMIT_MODIFIER
import io.direkt.service.context.RequestContextFactory.Companion.RECURSIVE_MODIFIER
import io.direkt.service.context.modifiers.DeleteModifiers
import io.direkt.service.context.modifiers.OrderBy
import io.direkt.service.context.modifiers.QueryModifiers
import io.direkt.service.context.modifiers.ReturnFormat
import io.direkt.service.context.modifiers.SpecifiedInRequest
import io.ktor.util.logging.KtorSimpleLogger

object PathModifierExtractor {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    fun extractQueryModifiers(path: String?): QueryModifiers {
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

    fun extractDeleteModifiers(path: String?): DeleteModifiers {
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

    private fun toLimit(segment: String): Int =
        if (segment == NO_LIMIT_MODIFIER) {
            -1
        } else {
            segment.toPositiveInt()
        }
}
