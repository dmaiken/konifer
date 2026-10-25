package io.konifer.domain.ports

import io.konifer.domain.asset.AssetLabels

interface AssetDeleter {
    suspend fun delete(command: DeleteAssetsCommand)
}

sealed interface DeleteAssetsCommand {
    data class Entry(
        val path: String,
        val entryId: Long,
    ) : DeleteAssetsCommand

    data class AtPath(
        val path: String,
        val labels: AssetLabels,
    ) : DeleteAssetsCommand

    data class Recursively(
        val path: String,
        val labels: AssetLabels,
    ) : DeleteAssetsCommand
}
