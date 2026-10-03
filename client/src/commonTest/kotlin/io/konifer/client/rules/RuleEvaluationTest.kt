package io.konifer.client.rules

import io.konifer.client.KoniferClient
import io.konifer.client.KoniferResult
import io.konifer.client.harness.assertSignatureParameter
import io.konifer.client.harness.httpClient
import io.konifer.client.internal.HmacSigningConfig
import io.konifer.client.internal.KoniferUrlSigner
import io.konifer.common.http.AssetSourceRequest
import io.konifer.common.http.ErrorResponse
import io.konifer.common.http.EvaluateRuleDefinitionsRequest
import io.konifer.common.http.HttpSource
import io.konifer.common.http.RuleDefinitionRequest
import io.konifer.common.http.S3Source
import io.konifer.common.image.ImageFormat
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
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.toByteArray
import kotlinx.coroutines.flow.flow
import kotlinx.io.IOException
import kotlinx.serialization.json.Json

@OptIn(InternalAPI::class)
class RuleEvaluationTest :
    FunSpec({
        val landscape = RuleDefinitionRequest("landscape", listOf("a desert landscape"), 0.7)
        val portrait = RuleDefinitionRequest("portrait", listOf("a person's face"), 0.8)

        test("URL evaluation posts JSON with all rule definitions and no signature") {
            val response = createEvaluateRulesResponse()
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        request.method shouldBe HttpMethod.Post
                        request.url.encodedPath shouldBe "/rule-evaluations"
                        assertSignatureParameter(request.url.parameters, false)
                        val body = request.body.shouldBeInstanceOf<TextContent>()
                        body.contentType.toString() shouldBe "application/json"
                        Json.decodeFromString<EvaluateRuleDefinitionsRequest>(body.text) shouldBe
                            EvaluateRuleDefinitionsRequest(
                                source = AssetSourceRequest(http = HttpSource(url = "https://example.com/image.png")),
                                definitions = listOf(landscape, portrait),
                            )
                        respond(Json.encodeToString(response), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                }
            val client = KoniferClient(httpClient, KoniferUrlSigner.create(HmacSigningConfig(secretKey = "secret")))

            client
                .ruleEvaluation()
                .fromUrl("https://example.com/image.png")
                .withDefinition(landscape)
                .withDefinitions(listOf(portrait))
                .evaluate() shouldBe KoniferResult.Success(response)
        }

        test("evaluation branches preserve their base and snapshot definition collections") {
            val response = createEvaluateRulesResponse()
            val requests = mutableListOf<EvaluateRuleDefinitionsRequest>()
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        val body = request.body.shouldBeInstanceOf<TextContent>()
                        requests += Json.decodeFromString<EvaluateRuleDefinitionsRequest>(body.text)
                        respond(Json.encodeToString(response), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                }
            val prompts = mutableListOf("a desert landscape")
            val definition = landscape.copy(prompts = prompts)
            val definitions = mutableListOf(portrait)
            val base =
                KoniferClient(httpClient)
                    .ruleEvaluation()
                    .fromUrl("https://example.com/image.png")
                    .withDefinition(definition)
            val combined = base.withDefinitions(definitions)
            val portraitOnly =
                KoniferClient(httpClient)
                    .ruleEvaluation()
                    .fromUrl("https://example.com/image.png")
                    .withDefinitions(definitions)

            prompts.clear()
            definitions.clear()

            base.evaluate() shouldBe KoniferResult.Success(response)
            combined.evaluate() shouldBe KoniferResult.Success(response)
            portraitOnly.evaluate() shouldBe KoniferResult.Success(response)
            base.evaluate() shouldBe KoniferResult.Success(response)

            val expectedBase =
                EvaluateRuleDefinitionsRequest(
                    source = AssetSourceRequest(http = HttpSource(url = "https://example.com/image.png")),
                    definitions = listOf(landscape),
                )
            requests shouldBe
                listOf(
                    expectedBase,
                    expectedBase.copy(definitions = listOf(landscape, portrait)),
                    expectedBase.copy(definitions = listOf(portrait)),
                    expectedBase,
                )
        }

        test("S3 ARN evaluation posts the S3 source") {
            val response = createEvaluateRulesResponse()
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        val body = request.body.shouldBeInstanceOf<TextContent>()
                        Json.decodeFromString<EvaluateRuleDefinitionsRequest>(body.text) shouldBe
                            EvaluateRuleDefinitionsRequest(
                                source = AssetSourceRequest(s3 = S3Source(arn = "arn:aws:s3:::images/example.png")),
                                definitions = listOf(landscape),
                            )
                        respond(Json.encodeToString(response), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                }

            KoniferClient(httpClient)
                .ruleEvaluation()
                .fromS3Arn("arn:aws:s3:::images/example.png")
                .withDefinition(landscape)
                .evaluate() shouldBe KoniferResult.Success(response)
        }

        test("byte upload sends metadata and content without signing") {
            val response = createEvaluateRulesResponse()
            val bytes = byteArrayOf(1, 2, 3)
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        request.method shouldBe HttpMethod.Post
                        request.url.encodedPath shouldBe "/rule-evaluations"
                        assertSignatureParameter(request.url.parameters, false)
                        val parts = request.body.shouldBeInstanceOf<MultiPartFormDataContent>().parts
                        val metadata = parts.filterIsInstance<PartData.FormItem>().single { it.name == "metadata" }
                        Json.decodeFromString<EvaluateRuleDefinitionsRequest>(metadata.value) shouldBe
                            EvaluateRuleDefinitionsRequest(definitions = listOf(landscape))
                        val asset = parts.filterIsInstance<PartData.BinaryChannelItem>().single { it.name == "asset" }
                        asset.headers[HttpHeaders.ContentType] shouldBe ImageFormat.PNG.mimeType
                        asset.provider().toByteArray() shouldBe bytes
                        respond(Json.encodeToString(response), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                }
            val client = KoniferClient(httpClient, KoniferUrlSigner.create(HmacSigningConfig(secretKey = "secret")))

            client
                .ruleEvaluation()
                .fromBytes(bytes, ImageFormat.PNG)
                .withDefinition(landscape)
                .evaluate() shouldBe KoniferResult.Success(response)
        }

        test("byte evaluations preserve their snapshot across caller mutations and repeated evaluations") {
            val response = createEvaluateRulesResponse()
            val bytes = byteArrayOf(1, 2, 3)
            val expected = bytes.copyOf()
            var requests = 0
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        requests++
                        val parts = request.body.shouldBeInstanceOf<MultiPartFormDataContent>().parts
                        val asset = parts.filterIsInstance<PartData.BinaryChannelItem>().single { it.name == "asset" }
                        asset.provider().toByteArray() shouldBe expected
                        asset.provider().toByteArray() shouldBe expected
                        respond(Json.encodeToString(response), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                }
            val evaluation =
                KoniferClient(httpClient)
                    .ruleEvaluation()
                    .fromBytes(bytes, ImageFormat.PNG)
                    .withDefinition(landscape)

            bytes.fill(0)
            evaluation.evaluate() shouldBe KoniferResult.Success(response)
            bytes.fill(9)
            evaluation.evaluate() shouldBe KoniferResult.Success(response)
            requests shouldBe 2
        }

        test("channel upload opens a fresh channel for each read and evaluation") {
            val response = createEvaluateRulesResponse()
            val bytes = byteArrayOf(4, 5, 6)
            var opens = 0
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        val parts = request.body.shouldBeInstanceOf<MultiPartFormDataContent>().parts
                        val asset = parts.filterIsInstance<PartData.BinaryChannelItem>().single { it.name == "asset" }
                        asset.headers[HttpHeaders.ContentType] shouldBe ImageFormat.JPEG.mimeType
                        asset.provider().toByteArray() shouldBe bytes
                        asset.provider().toByteArray() shouldBe bytes
                        respond(Json.encodeToString(response), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                }

            val evaluation =
                KoniferClient(httpClient)
                    .ruleEvaluation()
                    .fromChannel({
                        opens++
                        ByteReadChannel(bytes)
                    }, ImageFormat.JPEG)
                    .withDefinition(landscape)
            evaluation.evaluate() shouldBe KoniferResult.Success(response)
            evaluation.evaluate() shouldBe KoniferResult.Success(response)
            opens shouldBe 4
        }

        test("chunk upload collects a fresh flow for each read") {
            val response = createEvaluateRulesResponse()
            val bytes = byteArrayOf(4, 5, 6)
            var collections = 0
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        val parts = request.body.shouldBeInstanceOf<MultiPartFormDataContent>().parts
                        val asset = parts.filterIsInstance<PartData.BinaryChannelItem>().single { it.name == "asset" }
                        asset.headers[HttpHeaders.ContentType] shouldBe ImageFormat.JPEG.mimeType
                        asset.provider().toByteArray() shouldBe bytes
                        asset.provider().toByteArray() shouldBe bytes
                        respond(Json.encodeToString(response), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                }

            val evaluation =
                KoniferClient(httpClient)
                    .ruleEvaluation()
                    .fromChunks(
                        provider = {
                            flow {
                                collections++
                                emit(byteArrayOf(4, 5))
                                emit(byteArrayOf(6))
                            }
                        },
                        format = ImageFormat.JPEG,
                    ).withDefinition(landscape)
            evaluation.evaluate() shouldBe KoniferResult.Success(response)
            evaluation.evaluate() shouldBe KoniferResult.Success(response)
            collections shouldBe 4
        }

        test("evaluation maps HTTP errors and malformed responses") {
            val errorClient =
                httpClient {
                    MockEngine {
                        respond(
                            Json.encodeToString(ErrorResponse("rules unavailable")),
                            status = HttpStatusCode.BadRequest,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                }
            KoniferClient(errorClient)
                .ruleEvaluation()
                .fromUrl("https://example.com/image.png")
                .withDefinition(landscape)
                .evaluate() shouldBe KoniferResult.Failure.Http(400, "rules unavailable")

            val malformedClient =
                httpClient {
                    MockEngine { respond("not-json", headers = headersOf(HttpHeaders.ContentType, "application/json")) }
                }
            val malformed =
                KoniferClient(malformedClient)
                    .ruleEvaluation()
                    .fromUrl("https://example.com/image.png")
                    .withDefinition(landscape)
                    .evaluate()
            (malformed is KoniferResult.Failure.InvalidResponse) shouldBe true
        }

        test("evaluation maps connection failures to transport failures") {
            val httpClient = httpClient { MockEngine { throw IOException("offline") } }

            val result =
                KoniferClient(httpClient)
                    .ruleEvaluation()
                    .fromUrl("https://example.com/image.png")
                    .withDefinition(landscape)
                    .evaluate()
            (result is KoniferResult.Failure.Transport) shouldBe true
        }
    })
