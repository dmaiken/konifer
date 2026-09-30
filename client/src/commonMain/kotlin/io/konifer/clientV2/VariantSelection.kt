package io.konifer.clientV2

import io.konifer.clientV2.internal.appendVariantRequest
import io.konifer.clientV2.internal.signedUrl
import io.konifer.clientV2.model.KoniferV2Result
import io.konifer.clientV2.model.RequestedTransformation
import io.konifer.clientV2.model.toKoniferV2Result
import io.konifer.clientV2.request.AssetSelection
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

class VariantSelection internal constructor(
    private val infra: RequestInfrastructure,
    private val asset: AssetSelection,
    private val requestedTransformation: RequestedTransformation = RequestedTransformation.OriginalVariant,
) {
    suspend fun writeContentTo(destination: ByteWriteChannel): KoniferV2Result<Unit> {
        var completed = false
        try {
            val result =
                safely {
                    infra.httpClient
                        .prepareGet {
                            url.takeFrom(requestUrl(ReturnFormat.CONTENT))
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

    suspend fun contentBytes(): KoniferV2Result<ByteArray> =
        safely {
            infra.httpClient
                .prepareGet {
                    url.takeFrom(requestUrl(ReturnFormat.CONTENT))
                }.execute { response ->
                    if (response.status.isSuccess()) {
                        KoniferV2Result.Success(response.bodyAsBytes())
                    } else {
                        response.toKoniferV2Result<ByteArray>()
                    }
                }
        }

    // link and redirect modes return the same delivery URL, so I am not exposing a redirect() method
    suspend fun link(): KoniferV2Result<AssetLinkResponse> =
        safely {
            infra.httpClient
                .get {
                    url.takeFrom(requestUrl(ReturnFormat.LINK))
                    accept(ContentType.Application.Json)
                }.toKoniferV2Result()
        }

    private suspend fun requestUrl(returnFormat: ReturnFormat) =
        signedUrl(infra.urlSigner) {
            appendVariantRequest(asset, returnFormat, requestedTransformation)
        }

    private inline fun <T> safely(block: () -> KoniferV2Result<T>): KoniferV2Result<T> =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            KoniferV2Result.Failure.Transport(e)
        }
}
