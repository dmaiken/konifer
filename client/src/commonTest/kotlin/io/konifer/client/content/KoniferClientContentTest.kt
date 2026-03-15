package io.konifer.client.content

import io.konifer.client.KoniferClient
import io.konifer.client.KoniferResponse
import io.konifer.client.metadata.configureMockEngineHappy
import io.konifer.client.metadata.createMetadataResponse
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class KoniferClientContentTest :
    FunSpec({
        test("should be able to fetch content") {
            val serverResponse =
                listOf(
                    createMetadataResponse(),
                    createMetadataResponse(),
                )
            val mockEngine =
                configureMockEngineHappy(
                    expectedPath = "/assets/users/123/-/content",
                    response = serverResponse,
                )
            val httpClient =
                HttpClient(mockEngine) {
                    install(ContentNegotiation) {
                        json(Json)
                    }
                }

            val koniferClient = KoniferClient(httpClient)

            val response =
                koniferClient.getAssetMetadata(
                    path = "/users/123",
                    limit = 2,
                )
            response::class shouldBe KoniferResponse.Success::class
            (response as KoniferResponse.Success<*>).body shouldBe serverResponse
        }
    })
