package io.konifer.client.rules

import io.konifer.client.KoniferResult
import io.konifer.client.internal.RequestInfrastructure
import io.konifer.client.toKoniferV2Result
import io.konifer.common.http.AssetSourceRequest
import io.konifer.common.http.EvaluateRuleDefinitionsRequest
import io.konifer.common.http.EvaluateRuleDefinitionsResponse
import io.konifer.common.http.RuleDefinitionRequest
import io.ktor.client.request.forms.ChannelProvider
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

internal suspend fun evaluateRules(
    infra: RequestInfrastructure,
    source: RuleEvaluationSource,
    definitions: List<RuleDefinitionRequest>,
): KoniferResult<EvaluateRuleDefinitionsResponse> {
    val request =
        EvaluateRuleDefinitionsRequest(
            source = if (source is RuleEvaluationSource.External) source.toRequestSource() else AssetSourceRequest(),
            definitions = definitions,
        )
    if (source is RuleEvaluationSource.External) {
        require(
            !request.source.http.url
                .isNullOrBlank() ||
                !request.source.s3.arn
                    .isNullOrBlank(),
        ) { "Either http.url or s3.arn is required in request" }
    }
    val uploadJob = SupervisorJob(currentCoroutineContext()[Job])
    val uploadScope = CoroutineScope(currentCoroutineContext() + uploadJob)
    return try {
        infra.httpClient
            .post {
                url { appendPathSegments("rule-evaluations") }
                when (source) {
                    is RuleEvaluationSource.External -> {
                        contentType(ContentType.Application.Json)
                        setBody(request)
                    }

                    is RuleEvaluationSource.Upload -> {
                        contentType(ContentType.MultiPart.FormData)
                        setBody(ruleEvaluationFormData(request, source, uploadScope))
                    }
                }
            }.toKoniferV2Result()
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        KoniferResult.Failure.Transport(e)
    } finally {
        uploadScope.cancel()
    }
}

private fun ruleEvaluationFormData(
    request: EvaluateRuleDefinitionsRequest,
    source: RuleEvaluationSource.Upload,
    scope: CoroutineScope,
): MultiPartFormDataContent =
    MultiPartFormDataContent(
        formData {
            append(
                key = "metadata",
                value = Json.encodeToString(request),
                headers = Headers.build { append(HttpHeaders.ContentType, ContentType.Application.Json.toString()) },
            )
            append(
                key = "asset",
                value = ChannelProvider { source.channel(scope) },
                headers =
                    Headers.build {
                        append(HttpHeaders.ContentType, source.format.mimeType)
                        append(HttpHeaders.ContentDisposition, "filename=\"upload.bin\"")
                    },
            )
        },
    )
