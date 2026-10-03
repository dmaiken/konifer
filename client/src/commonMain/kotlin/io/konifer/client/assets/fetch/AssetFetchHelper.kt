package io.konifer.client.assets.fetch

import io.konifer.client.KoniferResult
import io.konifer.client.assets.AssetSelection
import io.konifer.client.internal.RequestInfrastructure
import io.konifer.client.internal.appendAssetPath
import io.konifer.client.internal.appendLabels
import io.konifer.client.internal.appendLimit
import io.konifer.client.internal.appendQuerySelectors
import io.konifer.client.internal.appendVariantRequest
import io.konifer.client.internal.signedUrl
import io.konifer.client.toKoniferV2Result
import io.konifer.common.http.AssetEntriesResponse
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
): KoniferResult<T> =
    try {
        val requestUrl =
            signedUrl(infra.urlSigner) {
                appendAssetPath(path)
                appendQuerySelectors(ReturnFormat.INFO, selector)
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
        KoniferResult.Failure.Transport(e)
    }

internal suspend fun fetchAssetEntries(
    infra: RequestInfrastructure,
    path: String,
    selector: FetchQuerySelector,
    labels: Map<String, String> = emptyMap(),
    limit: Int,
): KoniferResult<AssetEntriesResponse> =
    try {
        val requestUrl =
            signedUrl(infra.urlSigner) {
                appendAssetPath(path)
                appendQuerySelectors(ReturnFormat.ENTRIES, selector)
                appendLimit(limit)
                appendLabels(labels)
            }
        infra.httpClient
            .get {
                url.takeFrom(requestUrl)
                accept(ContentType.Application.Json)
            }.toKoniferV2Result<AssetEntriesResponse>()
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        KoniferResult.Failure.Transport(e)
    }

// Cancellation must terminate the destination before it propagates to the caller.
@Suppress("SuspendFunSwallowedCancellation")
internal suspend fun fetchAssetContentTo(
    infra: RequestInfrastructure,
    asset: AssetSelection,
    transformation: RequestedTransformation,
    destination: ByteWriteChannel,
    delivery: ContentDelivery,
): KoniferResult<Unit> =
    try {
        val result =
            safelyFetch {
                infra
                    .httpClientFor(delivery)
                    .prepareGet {
                        url.takeFrom(variantRequestUrl(infra, asset, delivery.returnFormat(), transformation))
                    }.execute { response ->
                        if (response.status.isSuccess()) {
                            response.bodyAsChannel().copyAndClose(destination)
                            KoniferResult.Success(Unit)
                        } else {
                            response.toKoniferV2Result<Unit>()
                        }
                    }
            }
        if (result is KoniferResult.Failure) {
            destination.cancel(result.transferFailureCause())
        }
        result
    } catch (failure: Throwable) {
        destination.cancel(failure)
        throw failure
    }

private fun KoniferResult.Failure.transferFailureCause(): Throwable =
    when (this) {
        is KoniferResult.Failure.Http -> IOException("Variant transfer failed with HTTP status $statusCode")
        is KoniferResult.Failure.Transport -> cause
        is KoniferResult.Failure.InvalidResponse -> cause
    }

internal suspend fun fetchAssetContentBytes(
    infra: RequestInfrastructure,
    asset: AssetSelection,
    transformation: RequestedTransformation,
    delivery: ContentDelivery,
): KoniferResult<ByteArray> =
    safelyFetch {
        infra
            .httpClientFor(delivery)
            .prepareGet {
                url.takeFrom(variantRequestUrl(infra, asset, delivery.returnFormat(), transformation))
            }.execute { response ->
                if (response.status.isSuccess()) {
                    KoniferResult.Success(response.bodyAsBytes())
                } else {
                    response.toKoniferV2Result<ByteArray>()
                }
            }
    }

internal suspend fun fetchAssetLink(
    infra: RequestInfrastructure,
    asset: AssetSelection,
    transformation: RequestedTransformation,
): KoniferResult<AssetLinkResponse> =
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

private fun ContentDelivery.returnFormat(): ReturnFormat =
    when (this) {
        ContentDelivery.THROUGH_KONIFER -> ReturnFormat.CONTENT
        ContentDelivery.FOLLOW_REDIRECT -> ReturnFormat.REDIRECT
    }

private inline fun <T> safelyFetch(block: () -> KoniferResult<T>): KoniferResult<T> =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        KoniferResult.Failure.Transport(e)
    }
