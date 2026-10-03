package io.konifer.client.assets.delete

import io.konifer.common.selector.Order

internal sealed interface AssetDeleteTarget {
    data class Entry(
        val entryId: Long,
    ) : AssetDeleteTarget

    data class AtPath(
        val order: Order,
        val labels: Map<String, String>,
        val limit: Int,
    ) : AssetDeleteTarget

    data class Recursive(
        val labels: Map<String, String>,
    ) : AssetDeleteTarget
}
