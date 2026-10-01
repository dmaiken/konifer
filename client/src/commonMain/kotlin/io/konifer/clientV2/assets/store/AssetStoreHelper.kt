package io.konifer.clientV2.assets.store

import io.konifer.clientV2.RequestInfrastructure
import io.konifer.clientV2.KoniferV2Result
import io.konifer.clientV2.toKoniferV2Result
import io.konifer.clientV2.internal.appendAssetPath
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
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

internal suspend fun storeAsset(
    infra: RequestInfrastructure,
    path: String,
    source: AssetContentSource,
    request: StoreAssetRequest,
): KoniferV2Result<AssetResponse> {
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
                        setBody(assetUploadFormData(request, source))
                    }
                }
            }.toKoniferV2Result()
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        KoniferV2Result.Failure.Transport(e)
    }
}

private fun assetUploadFormData(
    request: StoreAssetRequest,
    source: AssetByteContentSource,
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
                value = ChannelProvider { source.channel() },
                headers =
                    Headers.build {
                        append(HttpHeaders.ContentType, source.format.mimeType)
                        append(HttpHeaders.ContentDisposition, "filename=\"upload.bin\"")
                    },
            )
        },
    )
