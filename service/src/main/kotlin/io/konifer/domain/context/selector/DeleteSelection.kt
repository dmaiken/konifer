package io.konifer.domain.context.selector

sealed interface DeleteSelection {
    data class Entry(
        val entryId: Long,
    ) : DeleteSelection {
        init {
            require(entryId >= 0) { "Entry ID must be nonnegative" }
        }
    }

    data object AllAtPath : DeleteSelection

    data object Recursive : DeleteSelection
}
