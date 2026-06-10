package io.konifer.client.harness

import io.konifer.client.HmacSigningConfig
import io.konifer.client.KoniferClient
import io.konifer.client.KoniferUrlSigner
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

fun httpClient(mockEngineFn: () -> MockEngine) =
    HttpClient(mockEngineFn()) {
        install(ContentNegotiation) {
            json(Json)
        }
    }

suspend fun signedKoniferClient(httpClient: HttpClient) =
    KoniferClient(
        httpClient = httpClient,
        urlSigner =
            KoniferUrlSigner.create(
                HmacSigningConfig(secretKey = "secret"),
            ),
    )
