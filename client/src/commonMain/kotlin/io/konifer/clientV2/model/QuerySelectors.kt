package io.konifer.clientV2.model

import io.konifer.common.selector.Order

sealed interface QuerySelector

sealed interface FetchQuerySelector : QuerySelector

sealed interface DeleteQuerySelector : QuerySelector

class OrderBy(
    val orderBy: Order,
) : FetchQuerySelector,
    DeleteQuerySelector

class EntryId(
    val entryId: Long,
) : FetchQuerySelector,
    DeleteQuerySelector

data object None :
    FetchQuerySelector,
    DeleteQuerySelector

data object Recursive : DeleteQuerySelector
