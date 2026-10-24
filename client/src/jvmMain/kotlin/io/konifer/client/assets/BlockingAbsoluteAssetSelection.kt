package io.konifer.client.assets

import io.konifer.client.KoniferResult
import kotlinx.coroutines.runBlocking

/** Blocking selection of one asset entry by its path and entry ID. */
class BlockingAbsoluteAssetSelection internal constructor(
    private val selection: AbsoluteAssetSelection,
) : BlockingAssetSelection(selection) {
    /** Deletes this entry, including when it no longer exists. */
    fun delete(): KoniferResult<Unit> = runBlocking { selection.delete() }
}
