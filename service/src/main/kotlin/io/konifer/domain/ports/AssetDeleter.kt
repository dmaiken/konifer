package io.konifer.domain.ports

import io.konifer.common.selector.Order

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
        val labels: Map<String, String>,
        val order: Order,
        val limit: Int,
    ) : DeleteAssetsCommand

    data class Recursively(
        val path: String,
        val labels: Map<String, String>,
    ) : DeleteAssetsCommand
}
