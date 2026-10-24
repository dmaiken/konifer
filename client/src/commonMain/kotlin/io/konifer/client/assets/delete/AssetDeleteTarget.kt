package io.konifer.client.assets.delete

internal sealed interface AssetDeleteTarget {
    data class Entry(
        val entryId: Long,
    ) : AssetDeleteTarget

    data class AtPath(
        val labels: Map<String, String>,
    ) : AssetDeleteTarget

    data class Recursive(
        val labels: Map<String, String>,
    ) : AssetDeleteTarget
}
