package io.konifer.client.assets.delete

import io.konifer.client.KoniferResult
import io.konifer.client.internal.RequestInfrastructure
import io.konifer.client.internal.appendAssetPath
import io.konifer.client.internal.appendEntrySelector
import io.konifer.client.internal.appendLabels
import io.konifer.client.internal.appendLimit
import io.konifer.client.internal.appendOrderSelector
import io.konifer.client.internal.appendPathSeparator
import io.konifer.client.internal.appendRecursiveSelector
import io.konifer.client.toKoniferResult
import io.ktor.client.request.delete
import io.ktor.http.URLBuilder
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import kotlinx.io.IOException
import kotlin.coroutines.cancellation.CancellationException

internal suspend fun deleteAsset(
    infra: RequestInfrastructure,
    path: String,
    target: AssetDeleteTarget,
): KoniferResult<Unit> =
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
                        appendOrderSelector(target.order)
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
            KoniferResult.Success(Unit)
        } else {
            response.toKoniferResult()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        KoniferResult.Failure.Transport(e)
    }
