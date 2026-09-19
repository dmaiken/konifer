package io.konifer.infrastructure.datastore.postgres

import io.konifer.infrastructure.datastore.postgres.statement.AssetTargetSelector
import io.konifer.infrastructure.datastore.postgres.statement.DeleteStatementGenerator
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.jooq.DSLContext

object DeleteAssetHelper {
    suspend fun deleteAssets(
        dslContext: DSLContext,
        targets: AssetTargetSelector,
    ): Int =
        dslContext.contextualizedTransactionCoroutine {
            val processedParentIds =
                DeleteStatementGenerator
                    .deleteVariantsAndEnqueueOutbox(targets())
                    .asFlow()
                    .map { checkNotNull(it.value1()) }
                    .toList()

            processedParentIds
                .takeIf { it.isNotEmpty() }
                ?.let { ids ->
                    DeleteStatementGenerator
                        .deleteAssets(ids)
                        .awaitFirstOrNull()
                } ?: 0
        }
}
