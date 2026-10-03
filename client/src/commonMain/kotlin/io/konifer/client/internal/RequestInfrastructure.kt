package io.konifer.client.internal

import io.konifer.client.assets.fetch.ContentDelivery
import io.ktor.client.HttpClient

internal class RequestInfrastructure(
    val httpClient: HttpClient,
    val urlSigner: KoniferUrlSigner?,
) {
    private val redirectClient = lazy { httpClient.config { followRedirects = true } }

    fun httpClientFor(delivery: ContentDelivery): HttpClient =
        when (delivery) {
            ContentDelivery.THROUGH_KONIFER -> httpClient
            ContentDelivery.FOLLOW_REDIRECT -> redirectClient.value
        }

    fun close() {
        if (redirectClient.isInitialized()) redirectClient.value.close()
        httpClient.close()
    }
}
