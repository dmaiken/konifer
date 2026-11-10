package io.konifer.domain.context.selector

import io.konifer.domain.asset.EntryId

sealed interface DeleteSelection {
    data class Entry(
        val entryId: EntryId,
    ) : DeleteSelection

    data object AllAtPath : DeleteSelection

    data object Recursive : DeleteSelection
}
