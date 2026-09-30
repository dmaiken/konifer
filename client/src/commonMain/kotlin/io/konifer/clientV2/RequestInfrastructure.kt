package io.konifer.clientV2

import io.konifer.clientV2.internal.KoniferUrlSigner
import io.ktor.client.HttpClient

internal class RequestInfrastructure(
    val httpClient: HttpClient,
    val urlSigner: KoniferUrlSigner?,
)
