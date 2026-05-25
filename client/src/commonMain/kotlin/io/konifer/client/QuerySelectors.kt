package io.konifer.client

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

class None :
    FetchQuerySelector,
    DeleteQuerySelector

class Recursive : DeleteQuerySelector
