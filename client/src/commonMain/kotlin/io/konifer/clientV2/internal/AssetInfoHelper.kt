package io.konifer.clientV2.internal

import io.konifer.clientV2.RequestInfrastructure
import io.konifer.clientV2.model.FetchQuerySelector
import io.konifer.clientV2.model.KoniferV2Result
import io.konifer.clientV2.model.toKoniferV2Result
import io.konifer.common.selector.ReturnFormat
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.takeFrom
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
