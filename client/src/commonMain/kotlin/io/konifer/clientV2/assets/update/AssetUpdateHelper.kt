package io.konifer.clientV2.assets.update

import io.konifer.clientV2.KoniferV2Result
import io.konifer.clientV2.RequestInfrastructure
import io.konifer.clientV2.internal.appendAssetPath
import io.konifer.clientV2.internal.appendEntrySelector
import io.konifer.clientV2.internal.appendPathSeparator
import io.konifer.clientV2.toKoniferV2Result
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
): KoniferV2Result<AssetResponse> =
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
        KoniferV2Result.Failure.Transport(e)
    }
