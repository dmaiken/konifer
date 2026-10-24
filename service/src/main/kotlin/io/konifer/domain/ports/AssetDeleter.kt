package io.konifer.domain.ports

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
    ) : DeleteAssetsCommand

    data class Recursively(
        val path: String,
        val labels: Map<String, String>,
    ) : DeleteAssetsCommand
}
