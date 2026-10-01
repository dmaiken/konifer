package io.konifer.clientV2.rules

import io.konifer.client.harness.assertSignatureParameter
import io.konifer.client.harness.httpClient
import io.konifer.client.rule.createEvaluateRulesResponse
import io.konifer.clientV2.KoniferClientV2
import io.konifer.clientV2.KoniferV2Result
import io.konifer.clientV2.internal.HmacSigningConfig
import io.konifer.clientV2.internal.KoniferUrlSigner
import io.konifer.common.http.AssetSourceRequest
import io.konifer.common.http.ErrorResponse
import io.konifer.common.http.EvaluateRuleDefinitionsRequest
import io.konifer.common.http.HttpSource
import io.konifer.common.http.RuleDefinitionRequest
import io.konifer.common.http.S3Source
import io.konifer.common.image.ImageFormat
import io.kotest.assertions.throwables.shouldThrow
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
            val client = KoniferClientV2(httpClient, KoniferUrlSigner.create(HmacSigningConfig(secretKey = "secret")))

            client.ruleEvaluation()
                .fromUrl("https://example.com/image.png")
                .withDefinition(landscape)
                .withDefinitions(listOf(portrait))
                .evaluate() shouldBe KoniferV2Result.Success(response)
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

            KoniferClientV2(httpClient).ruleEvaluation()
                .fromS3Arn("arn:aws:s3:::images/example.png")
                .withDefinition(landscape)
                .evaluate() shouldBe KoniferV2Result.Success(response)
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
            val client = KoniferClientV2(httpClient, KoniferUrlSigner.create(HmacSigningConfig(secretKey = "secret")))

            client.ruleEvaluation().fromBytes(bytes, ImageFormat.PNG)
                .withDefinition(landscape).evaluate() shouldBe KoniferV2Result.Success(response)
        }

        test("channel upload sends the supplied image content type") {
            val response = createEvaluateRulesResponse()
            val bytes = byteArrayOf(4, 5, 6)
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        val parts = request.body.shouldBeInstanceOf<MultiPartFormDataContent>().parts
                        val asset = parts.filterIsInstance<PartData.BinaryChannelItem>().single { it.name == "asset" }
                        asset.headers[HttpHeaders.ContentType] shouldBe ImageFormat.JPEG.mimeType
                        asset.provider().toByteArray() shouldBe bytes
                        respond(Json.encodeToString(response), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                }

            KoniferClientV2(httpClient).ruleEvaluation()
                .fromChannel(ByteReadChannel(bytes), ImageFormat.JPEG)
                .withDefinition(landscape).evaluate() shouldBe KoniferV2Result.Success(response)
        }

        test("evaluation requires a source and one to ten definitions before making a request") {
            val httpClient = httpClient { MockEngine { error("No request expected") } }
            val client = KoniferClientV2(httpClient)

            shouldThrow<IllegalArgumentException> {
                client.ruleEvaluation().fromUrl(" ").withDefinition(landscape).evaluate()
            }
            shouldThrow<IllegalArgumentException> {
                client.ruleEvaluation().fromS3Arn(" ").withDefinition(landscape).evaluate()
            }
            shouldThrow<IllegalArgumentException> {
                client.ruleEvaluation().fromBytes(byteArrayOf(1), ImageFormat.PNG).evaluate()
            }
            shouldThrow<IllegalArgumentException> {
                client.ruleEvaluation().fromBytes(byteArrayOf(1), ImageFormat.PNG)
                    .withDefinitions(List(11) { landscape }).evaluate()
            }
        }

        test("evaluation maps HTTP errors and malformed responses") {
            val errorClient = httpClient {
                MockEngine {
                    respond(Json.encodeToString(ErrorResponse("rules unavailable")), status = HttpStatusCode.BadRequest,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }
            KoniferClientV2(errorClient).ruleEvaluation().fromUrl("https://example.com/image.png")
                .withDefinition(landscape).evaluate() shouldBe KoniferV2Result.Failure.Http(400, "rules unavailable")

            val malformedClient = httpClient {
                MockEngine { respond("not-json", headers = headersOf(HttpHeaders.ContentType, "application/json")) }
            }
            val malformed = KoniferClientV2(malformedClient).ruleEvaluation().fromUrl("https://example.com/image.png")
                .withDefinition(landscape).evaluate()
            (malformed is KoniferV2Result.Failure.InvalidResponse) shouldBe true
        }

        test("evaluation maps connection failures to transport failures") {
            val httpClient = httpClient { MockEngine { throw IOException("offline") } }

            val result = KoniferClientV2(httpClient).ruleEvaluation().fromUrl("https://example.com/image.png")
                .withDefinition(landscape).evaluate()
            (result is KoniferV2Result.Failure.Transport) shouldBe true
        }
    })
