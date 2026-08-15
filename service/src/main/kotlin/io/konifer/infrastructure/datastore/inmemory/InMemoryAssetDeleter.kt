package io.konifer.infrastructure.datastore.inmemory

import io.konifer.domain.ports.AssetDeleter
import io.konifer.domain.ports.DeleteAssetsCommand
import io.konifer.domain.ports.ObjectStore

class InMemoryAssetDeleter(
    private val objectStore: ObjectStore,
    private val assetRepository: InMemoryAssetRepository,
) : AssetDeleter {
    override suspend fun delete(command: DeleteAssetsCommand) {
        val grouped =
            assetRepository
                .deleteAndReturnObjectReferences(command)
                .groupByTo(mutableMapOf(), { it.bucket }) { it.key }

        for ((bucket, keys) in grouped) {
            objectStore.deleteAll(
                bucket = bucket,
                keys = keys,
            )
        }
    }
}
