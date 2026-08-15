package io.konifer.infrastructure.datastore.postgres

import io.konifer.domain.ports.AssetDeleter
import io.konifer.domain.ports.AssetRepository
import io.konifer.domain.ports.DeleteAssetsCommand

class PostgresAssetDeleter(
    private val assetRepository: AssetRepository,
) : AssetDeleter {
    /**
     * When assets are deleted within Postgres, the variant references to the object store are
     * saved to an outbox where scheduled variant deletion occurs on a scheduled basis
     */
    override suspend fun delete(command: DeleteAssetsCommand) {
        when (command) {
            is DeleteAssetsCommand.Entry ->
                assetRepository.deleteByPath(
                    path = command.path,
                    entryId = command.entryId,
                )
            is DeleteAssetsCommand.AtPath ->
                assetRepository.deleteAllByPath(
                    path = command.path,
                    labels = command.labels,
                    order = command.order,
                    limit = command.limit,
                )
            is DeleteAssetsCommand.Recursively ->
                assetRepository.deleteRecursivelyByPath(
                    path = command.path,
                    labels = command.labels,
                )
        }
    }
}
