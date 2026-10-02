package io.konifer.clientV2

import io.konifer.client.asset.info.createInfoResponse
import io.konifer.client.asset.link.createLinkResponse
import io.konifer.client.harness.httpClient
import io.konifer.client.rule.createEvaluateRulesResponse
import io.konifer.clientV2.assets.fetch.ContentDelivery
import io.konifer.common.http.AssetEntriesResponse
import io.konifer.common.http.ErrorResponse
import io.konifer.common.http.RuleDefinitionRequest
import io.konifer.common.http.StoreAssetRequest
import io.konifer.common.image.ImageFormat
import io.konifer.common.selector.Order
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.headersOf
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.toByteArray
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.function.Supplier

@OptIn(InternalAPI::class)
class KoniferBlockingClientV2Test :
    FunSpec({
        test("asset selections delegate info, entries, update, and delete") {
            val asset = createInfoResponse()
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        when {
                            request.method == HttpMethod.Get && request.url.encodedPath.endsWith("/info") -> {
                                request.url.encodedPath shouldBe "/assets/users/123/-/modified/info"
                                request.url.parameters["camera"] shouldBe "phone"
                                respond(Json.encodeToString(asset), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                            }

                            request.method == HttpMethod.Get && request.url.encodedPath.endsWith("/entries") -> {
                                request.url.parameters["limit"] shouldBe "2"
                                respond(
                                    Json.encodeToString(AssetEntriesResponse(listOf(asset))),
                                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                                )
                            }

                            request.method == HttpMethod.Put -> {
                                request.url.encodedPath shouldBe "/assets/users/123/-/entry/42"
                                respond(Json.encodeToString(asset), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                            }

                            request.method == HttpMethod.Delete -> {
                                request.url.encodedPath shouldBe "/assets/users/123/-/entry/42"
                                respond("", status = HttpStatusCode.NoContent)
                            }

                            else -> {
                                error("Unexpected request: ${request.method} ${request.url}")
                            }
                        }
                    }
                }
            val client = KoniferBlockingClientV2(KoniferClientV2(httpClient))

            val relative = client.asset("users/123").matchingLabels(mapOf("camera" to "phone")).orderBy(Order.MODIFIED)
            relative.info() shouldBe KoniferV2Result.Success(asset)
            relative.entries(2) shouldBe KoniferV2Result.Success(AssetEntriesResponse(listOf(asset)))
            val absolute = client.asset("users/123").entry(42)
            absolute.update(StoreAssetRequest(alt = "updated")) shouldBe KoniferV2Result.Success(asset)
            absolute.delete() shouldBe KoniferV2Result.Success(Unit)
            client.close()
        }

        test("variant selection delegates bytes, link, and OutputStream transfer") {
            val bytes = byteArrayOf(1, 2, 3, 4)
            val link = createLinkResponse()
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        when {
                            request.url.encodedPath.endsWith("/link") -> {
                                respond(Json.encodeToString(link), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                            }

                            request.url.encodedPath.endsWith("/redirect") -> {
                                request.url.encodedPath shouldBe "/assets/users/123/-/new/redirect"
                                respond(bytes, headers = headersOf(HttpHeaders.ContentType, "image/png"))
                            }

                            else -> {
                                request.url.encodedPath shouldBe "/assets/users/123/-/new/content"
                                respond(bytes, headers = headersOf(HttpHeaders.ContentType, "image/png"))
                            }
                        }
                    }
                }
            val client = KoniferBlockingClientV2(KoniferClientV2(httpClient))
            val variant = client.asset("users/123").originalVariant()

            variant.contentBytes() shouldBe KoniferV2Result.Success(bytes)
            variant.contentBytes(ContentDelivery.FOLLOW_REDIRECT) shouldBe KoniferV2Result.Success(bytes)
            variant.link() shouldBe KoniferV2Result.Success(link)
            val output = ByteArrayOutputStream()
            variant.writeContentTo(output) shouldBe KoniferV2Result.Success(Unit)
            output.toByteArray() shouldBe bytes
            client.close()
        }

        test("OutputStream transfer returns a V2 failure on an HTTP error") {
            val httpClient =
                httpClient {
                    MockEngine {
                        respond(
                            Json.encodeToString(ErrorResponse("not found")),
                            status = HttpStatusCode.NotFound,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                }
            val client = KoniferBlockingClientV2(KoniferClientV2(httpClient))
            val output = ByteArrayOutputStream()

            client.asset("users/123").originalVariant().writeContentTo(output) shouldBe
                KoniferV2Result.Failure.Http(404, "not found")
            output.size() shouldBe 0
            client.close()
        }

        test("new asset builder delegates byte and InputStream uploads") {
            val asset = createInfoResponse()
            val bytes = byteArrayOf(5, 6, 7)
            var streamOpens = 0
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        request.method shouldBe HttpMethod.Post
                        request.url.encodedPath shouldBe "/assets/users/123"
                        val content =
                            request.body
                                .shouldBeInstanceOf<MultiPartFormDataContent>()
                                .parts
                                .filterIsInstance<PartData.BinaryChannelItem>()
                                .single { it.name == "asset" }
                        content.provider().toByteArray() shouldBe bytes
                        respond(Json.encodeToString(asset), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                }
            val client = KoniferBlockingClientV2(KoniferClientV2(httpClient))

            client
                .asset("users/123")
                .newAsset()
                .fromBytes(bytes, ImageFormat.PNG)
                .withAlt("image")
                .store() shouldBe
                KoniferV2Result.Success(asset)
            val upload =
                client
                    .asset("users/123")
                    .newAsset()
                    .fromInputStream(
                        Supplier<InputStream> {
                            streamOpens++
                            ByteArrayInputStream(bytes)
                        },
                        ImageFormat.PNG,
                    ).withLabel("camera", "phone")
            upload.store() shouldBe KoniferV2Result.Success(asset)
            upload.store() shouldBe KoniferV2Result.Success(asset)
            streamOpens shouldBe 2
            client.close()
        }

        test("rule evaluation builder delegates URL and InputStream requests") {
            val response = createEvaluateRulesResponse()
            val bytes = byteArrayOf(9, 8, 7)
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        request.url.encodedPath shouldBe "/rule-evaluations"
                        if (request.body is MultiPartFormDataContent) {
                            val content =
                                request.body
                                    .shouldBeInstanceOf<MultiPartFormDataContent>()
                                    .parts
                                    .filterIsInstance<PartData.BinaryChannelItem>()
                                    .single { it.name == "asset" }
                            content.provider().toByteArray() shouldBe bytes
                        }
                        respond(Json.encodeToString(response), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                }
            val client = KoniferBlockingClientV2(KoniferClientV2(httpClient))
            val definition = RuleDefinitionRequest("portrait", listOf("a face"), 0.7)

            client
                .ruleEvaluation()
                .fromUrl("https://example.com/image.png")
                .withDefinition(definition)
                .evaluate() shouldBe
                KoniferV2Result.Success(response)
            client
                .ruleEvaluation()
                .fromInputStream(Supplier { ByteArrayInputStream(bytes) }, ImageFormat.JPEG)
                .withDefinition(definition)
                .evaluate() shouldBe KoniferV2Result.Success(response)
            client.close()
        }
    })
