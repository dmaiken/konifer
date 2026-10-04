package io.konifer.client.assets.fetch

import io.konifer.common.selector.Order

/** Selector encoded into a Konifer asset request path. */
sealed interface QuerySelector

/** Selector accepted by asset metadata fetch operations. */
sealed interface FetchQuerySelector : QuerySelector

/** Selector accepted by asset deletion operations. */
sealed interface DeleteQuerySelector : QuerySelector

/** Selects entries using [orderBy]. */
class OrderBy(
    val orderBy: Order,
) : FetchQuerySelector,
    DeleteQuerySelector

/** Selects the entry identified by [entryId]. */
class EntryId(
    val entryId: Long,
) : FetchQuerySelector,
    DeleteQuerySelector

/** Applies no entry selector. */
data object None :
    FetchQuerySelector,
    DeleteQuerySelector

/** Selects a path and all of its descendants for deletion. */
data object Recursive : DeleteQuerySelector
