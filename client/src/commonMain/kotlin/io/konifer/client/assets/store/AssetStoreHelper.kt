package io.konifer.client.assets.store

import io.konifer.client.KoniferResult
import io.konifer.client.internal.RequestInfrastructure
import io.konifer.client.internal.appendAssetPath
import io.konifer.client.toKoniferResult
import io.konifer.common.http.AssetResponse
import io.konifer.common.http.StoreAssetRequest
import io.ktor.client.request.forms.ChannelProvider
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.contentType
import io.ktor.http.takeFrom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

internal suspend fun storeAsset(
    infra: RequestInfrastructure,
    path: String,
    source: AssetContentSource,
    request: StoreAssetRequest,
): KoniferResult<AssetResponse> {
    if (source is AssetReferenceContentSource) {
        require(
            !request.source.http.url
                .isNullOrBlank() ||
                !request.source.s3.arn
                    .isNullOrBlank(),
        ) {
            "Either http.url or s3.arn is required in request"
        }
    }
    val uploadJob = SupervisorJob(currentCoroutineContext()[Job])
    val uploadScope = CoroutineScope(currentCoroutineContext() + uploadJob)
    return try {
        val requestUrl = URLBuilder().apply { appendAssetPath(path) }
        infra.httpClient
            .post {
                url.takeFrom(requestUrl)
                when (source) {
                    is AssetReferenceContentSource -> {
                        contentType(ContentType.Application.Json)
                        setBody(request)
                    }

                    is AssetByteContentSource -> {
                        contentType(ContentType.MultiPart.FormData)
                        setBody(assetUploadFormData(request, source, uploadScope))
                    }
                }
            }.toKoniferResult()
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        KoniferResult.Failure.Transport(e)
    } finally {
        uploadScope.cancel()
    }
}

private fun assetUploadFormData(
    request: StoreAssetRequest,
    source: AssetByteContentSource,
    scope: CoroutineScope,
): MultiPartFormDataContent =
    MultiPartFormDataContent(
        formData {
            append(
                key = "metadata",
                value = Json.encodeToString(request),
                headers = Headers.build { append(HttpHeaders.ContentType, ContentType.Application.Json.toString()) },
            )
            append(
                key = "asset",
                value = ChannelProvider { source.channel(scope) },
                headers =
                    Headers.build {
                        append(HttpHeaders.ContentType, source.format.mimeType)
                        append(HttpHeaders.ContentDisposition, "filename=\"upload.bin\"")
                    },
            )
        },
    )
