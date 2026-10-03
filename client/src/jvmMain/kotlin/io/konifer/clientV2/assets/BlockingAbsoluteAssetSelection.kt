package io.konifer.clientV2.assets

/** Blocking selection of one asset entry by its path and entry ID. */
class BlockingAbsoluteAssetSelection internal constructor(
    selection: AbsoluteAssetSelection,
) : BlockingAssetSelection(selection)
