package io.konifer.domain.context

import io.konifer.common.image.LIMIT_PARAMETER
import io.konifer.common.selector.DEFAULT_ENTRIES_LIMIT
import io.konifer.common.selector.Order
import io.konifer.common.selector.ReturnFormat
import io.konifer.domain.context.RequestContextFactory.Companion.ENTRY_ID_MODIFIER
import io.konifer.domain.context.RequestContextFactory.Companion.RECURSIVE_MODIFIER
import io.konifer.domain.context.selector.DeleteSelection
import io.konifer.domain.context.selector.QuerySelectors
import io.konifer.domain.context.selector.SpecifiedInRequest
import io.ktor.http.Parameters
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.debug

object PathSelectorExtractor {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    fun extractQuerySelectors(
        path: String?,
        parameters: Parameters,
    ): QuerySelectors {
        val limit = parameters.requestedLimit() ?: Limit.single
        val isLimitSpecified = parameters.contains(LIMIT_PARAMETER)
        if (path.isNullOrBlank()) {
            return QuerySelectors(
                limit = limit,
                specifiedModifiers =
                    SpecifiedInRequest(
                        limit = isLimitSpecified,
                    ),
            )
        }

        val querySelectorSegments = path.trim('/').uppercase().split('/')
        if (querySelectorSegments.size > 3) {
            throw InvalidQuerySelectorsException("Too many query modifiers: $querySelectorSegments")
        }

        val querySelectors =
            try {
                when (querySelectorSegments.size) {
                    3 -> {
                        if (querySelectorSegments[0] == ENTRY_ID_MODIFIER) {
                            QuerySelectors(
                                returnFormat = ReturnFormat.valueOf(querySelectorSegments[2]),
                                entryId = querySelectorSegments[1].toNonNegativeLong(),
                                limit = limit,
                                specifiedModifiers =
                                    SpecifiedInRequest(
                                        returnFormat = true,
                                        limit = isLimitSpecified,
                                    ),
                            )
                        } else {
                            throw InvalidQuerySelectorsException("Invalid query modifiers: $querySelectorSegments")
                        }
                    }

                    2 -> {
                        if (Order.valueOfOrNull(querySelectorSegments[0]) != null) {
                            QuerySelectors(
                                order = Order.valueOf(querySelectorSegments[0]),
                                returnFormat = ReturnFormat.valueOf(querySelectorSegments[1]),
                                limit = limit,
                                specifiedModifiers =
                                    SpecifiedInRequest(
                                        orderBy = true,
                                        returnFormat = true,
                                        limit = isLimitSpecified,
                                    ),
                            )
                        } else if (querySelectorSegments[0] == ENTRY_ID_MODIFIER) {
                            QuerySelectors(
                                entryId = querySelectorSegments[1].toNonNegativeLong(),
                                limit = limit,
                                specifiedModifiers =
                                    SpecifiedInRequest(
                                        limit = isLimitSpecified,
                                    ),
                            )
                        } else {
                            throw InvalidQuerySelectorsException("Invalid query modifiers: $querySelectorSegments")
                        }
                    }

                    1 -> {
                        if (ReturnFormat.valueOfOrNull(querySelectorSegments[0]) != null) {
                            QuerySelectors(
                                returnFormat = ReturnFormat.valueOf(querySelectorSegments[0]),
                                limit = limit,
                                specifiedModifiers =
                                    SpecifiedInRequest(
                                        returnFormat = true,
                                        limit = isLimitSpecified,
                                    ),
                            )
                        } else if (Order.valueOfOrNull(querySelectorSegments[0]) != null) {
                            QuerySelectors(
                                order = Order.valueOf(querySelectorSegments[0]),
                                limit = limit,
                                specifiedModifiers =
                                    SpecifiedInRequest(
                                        orderBy = true,
                                        limit = isLimitSpecified,
                                    ),
                            )
                        } else {
                            throw IllegalArgumentException("Invalid query modifiers: $querySelectorSegments")
                        }
                    }

                    else -> {
                        QuerySelectors()
                    } // Defaults
                }
            } catch (e: Exception) {
                throw InvalidQuerySelectorsException("Invalid query modifiers: $querySelectorSegments", e)
            }
        val effectiveSelectors =
            if (!isLimitSpecified && querySelectors.returnFormat == ReturnFormat.ENTRIES) {
                querySelectors.copy(limit = DEFAULT_ENTRIES_LIMIT.toLimit())
            } else {
                querySelectors
            }
        logger.debug { "Parsed query modifiers for path: $path - $effectiveSelectors" }

        return effectiveSelectors
    }

    fun extractDeleteSelectors(
        path: String?,
        parameters: Parameters,
    ): DeleteSelection {
        val limit = parameters.requestedLimit()
        if (path.isNullOrBlank()) {
            if (limit != Limit.unlimited) {
                throw InvalidDeleteSelectorsException(
                    "Specify an entry ID, recursive deletion, or limit=-1 to delete all entries at the exact path",
                )
            }
            return DeleteSelection.AllAtPath
        }
        if (limit != null) {
            throw InvalidDeleteSelectorsException("Limit cannot be supplied with an entry ID or recursive deletion")
        }
        val deleteModifierSegments = path.trim('/').uppercase().split('/')
        if (deleteModifierSegments.size > 2) {
            throw InvalidDeleteSelectorsException("Too many delete modifiers: $deleteModifierSegments")
        }

        val deleteModifiers =
            try {
                when (deleteModifierSegments.size) {
                    2 -> {
                        if (deleteModifierSegments[0] == ENTRY_ID_MODIFIER) {
                            DeleteSelection.Entry(checkNotNull(deleteModifierSegments[1].toNonNegativeLong()))
                        } else {
                            throw InvalidDeleteSelectorsException("Invalid delete modifiers: $deleteModifierSegments")
                        }
                    }

                    1 -> {
                        if (deleteModifierSegments[0] == RECURSIVE_MODIFIER) {
                            DeleteSelection.Recursive
                        } else {
                            throw InvalidDeleteSelectorsException("Invalid delete modifiers: $deleteModifierSegments")
                        }
                    }

                    else -> {
                        throw InvalidDeleteSelectorsException("Invalid delete modifiers: $deleteModifierSegments")
                    }
                }
            } catch (e: Exception) {
                throw InvalidDeleteSelectorsException("Invalid delete modifiers: $deleteModifierSegments", e)
            }
        logger.debug { "Parsed delete modifiers for path: $path - $deleteModifiers" }

        return deleteModifiers
    }

    private fun Parameters.requestedLimit(): Limit? {
        val values = getAll(LIMIT_PARAMETER) ?: return null
        require(values.size == 1) { "Limit must be supplied exactly once" }
        return values.single().toLimit()
    }
}
