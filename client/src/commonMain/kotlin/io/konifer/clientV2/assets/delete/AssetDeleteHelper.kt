package io.konifer.clientV2.assets.delete

import io.konifer.clientV2.RequestInfrastructure
import io.konifer.clientV2.internal.appendAssetPath
import io.konifer.clientV2.internal.appendEntrySelector
import io.konifer.clientV2.internal.appendLabels
import io.konifer.clientV2.internal.appendLimit
import io.konifer.clientV2.internal.appendPathSeparator
import io.konifer.clientV2.internal.appendRecursiveSelector
import io.konifer.clientV2.KoniferV2Result
import io.konifer.clientV2.toKoniferV2Result
import io.konifer.common.selector.Order
import io.ktor.client.request.delete
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import kotlinx.io.IOException
import kotlin.coroutines.cancellation.CancellationException

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

internal suspend fun deleteAsset(
    infra: RequestInfrastructure,
    path: String,
    target: AssetDeleteTarget,
): KoniferV2Result<Unit> =
    try {
        val requestUrl =
            URLBuilder().apply {
                appendAssetPath(path)
                when (target) {
                    is AssetDeleteTarget.Entry -> {
                        appendPathSeparator()
                        appendEntrySelector(target.entryId)
                    }

                    is AssetDeleteTarget.AtPath -> {
                        appendPathSeparator()
                        appendPathSegments(target.order.name.lowercase())
                        appendLabels(target.labels)
                        appendLimit(target.limit)
                    }

                    is AssetDeleteTarget.Recursive -> {
                        appendPathSeparator()
                        appendRecursiveSelector()
                        appendLabels(target.labels)
                    }
                }
            }
        val response = infra.httpClient.delete { url.takeFrom(requestUrl) }
        if (response.status.isSuccess()) {
            KoniferV2Result.Success(Unit)
        } else {
            response.toKoniferV2Result()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        KoniferV2Result.Failure.Transport(e)
    }
