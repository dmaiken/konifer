package io.konifer.clientV2.assets.fetch

import io.konifer.common.selector.Order

sealed interface QuerySelector

sealed interface FetchQuerySelector : QuerySelector

sealed interface DeleteQuerySelector : QuerySelector

internal class OrderBy(
    val orderBy: Order,
) : FetchQuerySelector,
    DeleteQuerySelector

internal class EntryId(
    val entryId: Long,
) : FetchQuerySelector,
    DeleteQuerySelector

internal data object None :
    FetchQuerySelector,
    DeleteQuerySelector

internal data object Recursive : DeleteQuerySelector
