package io.konifer.application.usecase.delete

import io.konifer.domain.context.DeleteRequestContext
import io.konifer.domain.context.selector.DeleteSelection
import io.konifer.domain.ports.AssetDeleter
import io.konifer.domain.ports.DeleteAssetsCommand
import io.ktor.util.logging.KtorSimpleLogger

class DeleteAssetUseCase(
    private val assetDeleter: AssetDeleter,
) {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    suspend fun deleteAssets(context: DeleteRequestContext) {
        val command =
            when (val selection = context.selection) {
                is DeleteSelection.Entry -> {
                    logger.info("Deleting asset with path: ${context.path} and entryId: ${selection.entryId}")
                    DeleteAssetsCommand.Entry(
                        path = context.path,
                        entryId = selection.entryId,
                    )
                }

                DeleteSelection.Recursive -> {
                    logger.info("Deleting assets recursively at path: ${context.path} with labels: ${context.labels}")
                    DeleteAssetsCommand.Recursively(
                        path = context.path,
                        labels = context.labels,
                    )
                }

                DeleteSelection.AllAtPath -> {
                    logger.info(
                        "Deleting all assets at path: ${context.path} with labels: ${context.labels}",
                    )
                    DeleteAssetsCommand.AtPath(
                        path = context.path,
                        labels = context.labels,
                    )
                }
            }

        assetDeleter.delete(command)
    }
}
