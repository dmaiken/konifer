package io.konifer.client.assets.update

import io.konifer.client.KoniferResult
import io.konifer.client.internal.RequestInfrastructure
import io.konifer.client.internal.appendAssetPath
import io.konifer.client.internal.appendEntrySelector
import io.konifer.client.internal.appendPathSeparator
import io.konifer.client.toKoniferV2Result
import io.konifer.common.http.AssetResponse
import io.konifer.common.http.StoreAssetRequest
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.URLBuilder
import io.ktor.http.contentType
import io.ktor.http.takeFrom
import kotlinx.io.IOException
import kotlin.coroutines.cancellation.CancellationException

internal suspend fun updateAsset(
    infra: RequestInfrastructure,
    path: String,
    entryId: Long,
    request: StoreAssetRequest,
): KoniferResult<AssetResponse> =
    try {
        val requestUrl =
            URLBuilder().apply {
                appendAssetPath(path)
                appendPathSeparator()
                appendEntrySelector(entryId)
            }
        infra.httpClient
            .put {
                url.takeFrom(requestUrl)
                contentType(ContentType.Application.Json)
                setBody(request)
            }.toKoniferV2Result()
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        KoniferResult.Failure.Transport(e)
    }
