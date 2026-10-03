package io.konifer.clientV2.asset.variant

import io.konifer.client.asset.link.createLinkResponse
import io.konifer.client.harness.httpClient
import io.konifer.clientV2.KoniferClientV2
import io.konifer.clientV2.KoniferV2Result
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.serialization.json.Json

class OriginalVariantTest :
    FunSpec({
        test("relative selection fetches original content without transformation parameters") {
            val bytes = byteArrayOf(1, 2, 3)
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        request.url.encodedPath shouldBe "/assets/users/123/-/new/content"
                        request.url.parameters.names() shouldBe emptySet()
                        respond(bytes, headers = headersOf(HttpHeaders.ContentType, "image/png"))
                    }
                }

            KoniferClientV2(httpClient)
                .assets("users/123")
                .originalVariant()
                .fetchContentBytes() shouldBe KoniferV2Result.Success(bytes)
        }

        test("entry selection fetches an original variant link without transformation parameters") {
            val link = createLinkResponse()
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        request.url.encodedPath shouldBe "/assets/users/123/-/entry/42/link"
                        request.url.parameters.names() shouldBe emptySet()
                        respond(Json.encodeToString(link), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                }

            KoniferClientV2(httpClient)
                .assets("users/123")
                .entry(42)
                .originalVariant()
                .fetchLink() shouldBe KoniferV2Result.Success(link)
        }
    })
