package io.konifer.service.context

import io.konifer.service.context.RequestContextFactory.Companion.ENTRY_ID_MODIFIER
import io.konifer.service.context.RequestContextFactory.Companion.RECURSIVE_MODIFIER
import io.konifer.service.context.selector.DEFAULT_LIMIT
import io.konifer.service.context.selector.DeleteModifiers
import io.konifer.service.context.selector.LIMIT_PARAMETER
import io.konifer.service.context.selector.Order
import io.konifer.service.context.selector.QuerySelectors
import io.konifer.service.context.selector.ReturnFormat
import io.konifer.service.context.selector.SpecifiedInRequest
import io.ktor.http.Parameters
import io.ktor.util.logging.KtorSimpleLogger
import kotlin.text.toIntOrNull

object PathSelectorExtractor {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    fun extractQuerySelectors(
        path: String?,
        parameters: Parameters,
    ): QuerySelectors {
        val limit = parameters[LIMIT_PARAMETER]?.toIntOrNull() ?: DEFAULT_LIMIT
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
                    1 ->
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
                    else -> QuerySelectors() // Defaults
                }
            } catch (e: Exception) {
                throw InvalidQuerySelectorsException("Invalid query modifiers: $querySelectorSegments", e)
            }
        logger.info("Parsed query modifiers for path: $path - $querySelectors")

        return querySelectors
    }

    fun extractDeleteSelectors(
        path: String?,
        parameters: Parameters,
    ): DeleteModifiers {
        val limit = parameters[LIMIT_PARAMETER]?.toIntOrNull() ?: DEFAULT_LIMIT
        if (path.isNullOrBlank()) {
            return DeleteModifiers(
                limit = limit,
            )
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
                            DeleteModifiers(
                                entryId = deleteModifierSegments[1].toNonNegativeLong(),
                                limit = limit,
                            )
                        } else {
                            throw InvalidDeleteSelectorsException("Invalid delete modifiers: $deleteModifierSegments")
                        }
                    }
                    1 -> {
                        if (deleteModifierSegments[0] == RECURSIVE_MODIFIER) {
                            DeleteModifiers(
                                recursive = true,
                                limit = limit,
                            )
                        } else if (Order.valueOfOrNull(deleteModifierSegments[0]) != null) {
                            DeleteModifiers(
                                order = Order.valueOf(deleteModifierSegments[0]),
                                limit = limit,
                            )
                        } else {
                            throw InvalidDeleteSelectorsException("Invalid delete modifiers: $deleteModifierSegments")
                        }
                    }
                    else -> DeleteModifiers()
                }
            } catch (e: Exception) {
                throw InvalidDeleteSelectorsException("Invalid delete modifiers: $deleteModifierSegments", e)
            }
        logger.info("Parsed delete modifiers for path: $path - $deleteModifiers")

        return deleteModifiers
    }
}
