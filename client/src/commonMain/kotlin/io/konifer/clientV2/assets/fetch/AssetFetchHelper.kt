package io.konifer.clientV2.assets.fetch

import io.konifer.clientV2.RequestInfrastructure
import io.konifer.clientV2.KoniferV2Result
import io.konifer.clientV2.assets.AssetSelection
import io.konifer.clientV2.internal.appendAssetPath
import io.konifer.clientV2.internal.appendLabels
import io.konifer.clientV2.internal.appendLimit
import io.konifer.clientV2.internal.appendQuerySelectors
import io.konifer.clientV2.internal.appendVariantRequest
import io.konifer.clientV2.internal.signedUrl
import io.konifer.clientV2.toKoniferV2Result
import io.konifer.common.http.AssetLinkResponse
import io.konifer.common.selector.ReturnFormat
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.copyAndClose
import kotlinx.io.IOException
import kotlin.coroutines.cancellation.CancellationException

internal suspend inline fun <reified T> fetchAssetInfo(
    infra: RequestInfrastructure,
    path: String,
    selector: FetchQuerySelector,
    labels: Map<String, String> = emptyMap(),
    limit: Int? = null,
): KoniferV2Result<T> =
    try {
        val requestUrl =
            signedUrl(infra.urlSigner) {
                appendAssetPath(path)
                appendQuerySelectors(ReturnFormat.INFO, selector)
                limit?.let { appendLimit(it) }
                appendLabels(labels)
            }
        infra.httpClient
            .get {
                url.takeFrom(requestUrl)
                accept(ContentType.Application.Json)
            }.toKoniferV2Result()
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        KoniferV2Result.Failure.Transport(e)
    }

internal suspend fun fetchAssetContentTo(
    infra: RequestInfrastructure,
    asset: AssetSelection,
    transformation: RequestedTransformation,
    destination: ByteWriteChannel,
): KoniferV2Result<Unit> {
    var completed = false
    try {
        val result =
            safelyFetch {
                infra.httpClient
                    .prepareGet {
                        url.takeFrom(variantRequestUrl(infra, asset, ReturnFormat.CONTENT, transformation))
                    }.execute { response ->
                        if (response.status.isSuccess()) {
                            response.bodyAsChannel().copyAndClose(destination)
                            KoniferV2Result.Success(Unit)
                        } else {
                            response.toKoniferV2Result<Unit>()
                        }
                    }
            }
        completed = result is KoniferV2Result.Success
        return result
    } finally {
        if (!completed) {
            destination.cancel(CancellationException("Variant transfer failed"))
        }
    }
}

internal suspend fun fetchAssetContentBytes(
    infra: RequestInfrastructure,
    asset: AssetSelection,
    transformation: RequestedTransformation,
): KoniferV2Result<ByteArray> =
    safelyFetch {
        infra.httpClient
            .prepareGet {
                url.takeFrom(variantRequestUrl(infra, asset, ReturnFormat.CONTENT, transformation))
            }.execute { response ->
                if (response.status.isSuccess()) {
                    KoniferV2Result.Success(response.bodyAsBytes())
                } else {
                    response.toKoniferV2Result<ByteArray>()
                }
            }
    }

internal suspend fun fetchAssetLink(
    infra: RequestInfrastructure,
    asset: AssetSelection,
    transformation: RequestedTransformation,
): KoniferV2Result<AssetLinkResponse> =
    safelyFetch {
        infra.httpClient
            .get {
                url.takeFrom(variantRequestUrl(infra, asset, ReturnFormat.LINK, transformation))
                accept(ContentType.Application.Json)
            }.toKoniferV2Result()
    }

private suspend fun variantRequestUrl(
    infra: RequestInfrastructure,
    asset: AssetSelection,
    returnFormat: ReturnFormat,
    transformation: RequestedTransformation,
) = signedUrl(infra.urlSigner) { appendVariantRequest(asset, returnFormat, transformation) }

private inline fun <T> safelyFetch(block: () -> KoniferV2Result<T>): KoniferV2Result<T> =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        KoniferV2Result.Failure.Transport(e)
    }
