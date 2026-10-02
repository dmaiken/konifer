package io.konifer.clientV2.upload

import io.konifer.client.asset.info.createInfoResponse
import io.konifer.client.harness.httpClient
import io.konifer.client.rule.createEvaluateRulesResponse
import io.konifer.clientV2.KoniferClientV2
import io.konifer.clientV2.KoniferV2Result
import io.konifer.clientV2.assets.store.fromInputStream
import io.konifer.clientV2.rules.fromInputStream
import io.konifer.common.http.RuleDefinitionRequest
import io.konifer.common.image.ImageFormat
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.http.HttpHeaders
import io.ktor.http.content.PartData
import io.ktor.http.headersOf
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.toByteArray
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream

@OptIn(InternalAPI::class)
class InputStreamUploadTest :
    FunSpec({
        test("asset upload opens and closes a fresh input stream each time") {
            val response = createInfoResponse()
            val bytes = byteArrayOf(1, 2, 3)
            var opens = 0
            var closes = 0
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        val content =
                            request.body
                                .shouldBeInstanceOf<MultiPartFormDataContent>()
                                .parts
                                .filterIsInstance<PartData.BinaryChannelItem>()
                                .single { it.name == "asset" }
                        content.provider().toByteArray() shouldBe bytes
                        respond(Json.encodeToString(response), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                }
            val upload =
                KoniferClientV2(httpClient).asset("users/123").newAsset().fromInputStream(
                    open = {
                        opens++
                        object : ByteArrayInputStream(bytes) {
                            override fun close() {
                                closes++
                                super.close()
                            }
                        }
                    },
                    format = ImageFormat.PNG,
                )

            upload.store() shouldBe KoniferV2Result.Success(response)
            upload.store() shouldBe KoniferV2Result.Success(response)
            opens shouldBe 2
            closes shouldBe 2
        }

        test("rule evaluation opens and closes a fresh input stream each time") {
            val response = createEvaluateRulesResponse()
            val bytes = byteArrayOf(4, 5, 6)
            var opens = 0
            var closes = 0
            val httpClient =
                httpClient {
                    MockEngine { request ->
                        val content =
                            request.body
                                .shouldBeInstanceOf<MultiPartFormDataContent>()
                                .parts
                                .filterIsInstance<PartData.BinaryChannelItem>()
                                .single { it.name == "asset" }
                        content.provider().toByteArray() shouldBe bytes
                        respond(Json.encodeToString(response), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                }
            val evaluation =
                KoniferClientV2(httpClient)
                    .ruleEvaluation()
                    .fromInputStream(
                        open = {
                            opens++
                            object : ByteArrayInputStream(bytes) {
                                override fun close() {
                                    closes++
                                    super.close()
                                }
                            }
                        },
                        format = ImageFormat.JPEG,
                    ).withDefinition(RuleDefinitionRequest("portrait", listOf("a person"), 0.7))

            evaluation.evaluate() shouldBe KoniferV2Result.Success(response)
            evaluation.evaluate() shouldBe KoniferV2Result.Success(response)
            opens shouldBe 2
            closes shouldBe 2
        }
    })
