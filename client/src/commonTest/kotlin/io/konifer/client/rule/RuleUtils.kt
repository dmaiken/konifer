package io.konifer.client.rule

import io.konifer.common.http.EvaluateRuleDefinitionsRequest
import io.konifer.common.http.EvaluateRuleDefinitionsResponse
import io.konifer.common.http.EvaluatedPromptResponse
import io.konifer.common.http.EvaluatedRuleDefinitionResponse
import io.konifer.common.http.RuleDefinitionRequest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json

fun createEvaluateRulesRequest(url: String? = null): EvaluateRuleDefinitionsRequest =
    EvaluateRuleDefinitionsRequest(
        url = url,
        definitions =
            listOf(
                RuleDefinitionRequest(
                    name = "is-landscape",
                    prompts = listOf("image contains a desert landscape"),
                    threshold = 0.7,
                ),
            ),
    )

fun createEvaluateRulesResponse(): EvaluateRuleDefinitionsResponse =
    EvaluateRuleDefinitionsResponse(
        results =
            listOf(
                EvaluatedRuleDefinitionResponse(
                    name = "is-landscape",
                    threshold = 0.7,
                    score = 0.91,
                    matched = true,
                    promptScores =
                        listOf(
                            EvaluatedPromptResponse(
                                prompt = "image contains a desert landscape",
                                score = 0.91,
                            ),
                        ),
                ),
            ),
    )

fun configureMockMultipartEngineHappy(
    request: EvaluateRuleDefinitionsRequest,
    assetBytes: ByteArray,
    response: EvaluateRuleDefinitionsResponse,
    statusCode: HttpStatusCode = HttpStatusCode.OK,
): MockEngine =
    MockEngine { httpRequest ->
        httpRequest.url.encodedPath shouldBe "/rule-evaluations"
        httpRequest.method shouldBe HttpMethod.Post

        val body = httpRequest.body.shouldBeInstanceOf<MultiPartFormDataContent>()
        val multipartBytes = body.toByteArray()
        val multipartText = multipartBytes.decodeToString()

        multipartText.contains("name=\"metadata\"") shouldBe true
        multipartText.contains(Json.encodeToString(request)) shouldBe true
        multipartText.contains("name=\"asset\"") shouldBe true
        multipartBytes.containsSubsequence(assetBytes) shouldBe true

        respond(
            content = Json.encodeToString(response),
            status = statusCode,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }

suspend fun MultiPartFormDataContent.toByteArray(): ByteArray =
    coroutineScope {
        val channel = ByteChannel(autoFlush = true)
        val writeJob =
            async {
                writeTo(channel)
                channel.close()
            }

        val bytes = channel.readRemaining().readByteArray()
        writeJob.await()
        bytes
    }

private fun ByteArray.containsSubsequence(subsequence: ByteArray): Boolean {
    if (subsequence.isEmpty()) return true
    if (subsequence.size > size) return false

    return indices
        .asSequence()
        .take(size - subsequence.size + 1)
        .any { startIndex ->
            subsequence.indices.all { offset ->
                this[startIndex + offset] == subsequence[offset]
            }
        }
}

fun configureMockUrlEngineHappy(
    request: EvaluateRuleDefinitionsRequest,
    response: EvaluateRuleDefinitionsResponse,
    statusCode: HttpStatusCode = HttpStatusCode.OK,
): MockEngine =
    MockEngine { httpRequest ->
        httpRequest.url.encodedPath shouldBe "/rule-evaluations"
        httpRequest.method shouldBe HttpMethod.Post

        val body = httpRequest.body.shouldBeInstanceOf<TextContent>()
        Json.decodeFromString<EvaluateRuleDefinitionsRequest>(body.text) shouldBe request

        respond(
            content = Json.encodeToString(response),
            status = statusCode,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }
