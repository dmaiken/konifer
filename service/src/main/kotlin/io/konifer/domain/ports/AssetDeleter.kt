package io.konifer.domain.ports

import io.konifer.domain.asset.AssetLabels
import io.konifer.domain.asset.AssetPath

interface AssetDeleter {
    suspend fun delete(command: DeleteAssetsCommand)
}

sealed interface DeleteAssetsCommand {
    data class Entry(
        val path: AssetPath,
        val entryId: Long,
    ) : DeleteAssetsCommand

    data class AtPath(
        val path: AssetPath,
        val labels: AssetLabels,
    ) : DeleteAssetsCommand

    data class Recursively(
        val path: AssetPath,
        val labels: AssetLabels,
    ) : DeleteAssetsCommand
}
