package io.konifer.entrypoint

import io.konifer.application.usecase.evaluate.EvaluateRuleDefinitionUseCase
import io.konifer.common.http.EvaluateRuleDefinitionsRequest
import io.konifer.common.http.EvaluateRuleDefinitionsResponse
import io.konifer.domain.asset.AssetDataContainer
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.Application
import io.ktor.server.request.contentType
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.utils.io.ByteChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject
import kotlin.coroutines.cancellation.CancellationException

const val RULE_EVALUATIONS_PATH = "/rule-evaluations"

private const val METADATA_PART_NAME = "metadata"
private const val ASSET_PART_NAME = "asset"

private val logger = KtorSimpleLogger("io.konifer.entrypoint.RuleEvaluationRoutes")

fun Application.configureRuleEvaluationRouting(maxMultipartContentLength: Long) {
    logger.info("Evaluating rule evaluation routes")
    val evaluateRuleDefinitionUseCase by inject<EvaluateRuleDefinitionUseCase>()

    routing {
        route(RULE_EVALUATIONS_PATH) {
            post {
                call.evaluateRuleDefinitions(
                    evaluateRuleDefinitionUseCase = evaluateRuleDefinitionUseCase,
                    maxMultipartContentLength = maxMultipartContentLength,
                )
            }
        }
    }
}

private suspend fun RoutingCall.evaluateRuleDefinitions(
    evaluateRuleDefinitionUseCase: EvaluateRuleDefinitionUseCase,
    maxMultipartContentLength: Long,
) {
    when (request.contentType().withoutParameters()) {
        ContentType.MultiPart.FormData -> {
            logger.info("Received multipart request to evaluate rule definitions")
            evaluateMultipartRuleDefinitions(
                evaluateRuleDefinitionUseCase = evaluateRuleDefinitionUseCase,
                maxMultipartContentLength = maxMultipartContentLength,
            )?.let { response ->
                respond(HttpStatusCode.OK, response)
            }
        }
        ContentType.Application.Json -> {
            logger.info("Received json request to evaluate rule definitions")
            val payload = receive(EvaluateRuleDefinitionsRequest::class)
            val response =
                evaluateRuleDefinitionUseCase.handleFromUrl(
                    request = payload,
                )
            respond(HttpStatusCode.OK, response)
        }
        else -> respond(HttpStatusCode.UnsupportedMediaType)
    }
}

private suspend fun RoutingCall.evaluateMultipartRuleDefinitions(
    evaluateRuleDefinitionUseCase: EvaluateRuleDefinitionUseCase,
    maxMultipartContentLength: Long,
): EvaluateRuleDefinitionsResponse? =
    coroutineScope {
        val evaluationRequest = CompletableDeferred<EvaluateRuleDefinitionsRequest>()
        val contentChannel = ByteChannel(true)
        var assetPartReceived = false
        var assetReceived = false
        var duplicateAssetReceived = false

        val deferredResponse =
            async {
                evaluateRuleDefinitionUseCase.handleFromUpload(
                    deferredRequest = evaluationRequest,
                    multiPartContainer = AssetDataContainer(contentChannel, maxMultipartContentLength),
                )
            }

        receiveMultipart().forEachPart { part ->
            when (part.name) {
                METADATA_PART_NAME -> part.readEvaluationRequestInto(evaluationRequest)
                ASSET_PART_NAME -> {
                    if (assetPartReceived) {
                        duplicateAssetReceived = true
                        part.release()
                    } else {
                        assetPartReceived = true
                        assetReceived = part.copyAssetContentTo(contentChannel)
                    }
                }
                else -> part.release()
            }
        }

        when {
            duplicateAssetReceived -> {
                contentChannel.cancel(CancellationException("Duplicate request payload"))
                deferredResponse.cancel()
                respond(HttpStatusCode.BadRequest, "Multiple request payloads supplied")
                null
            }
            !evaluationRequest.isCompleted -> {
                contentChannel.cancel(CancellationException("Missing metadata"))
                deferredResponse.cancel()
                respond(HttpStatusCode.BadRequest, "No request metadata supplied")
                null
            }
            !assetReceived -> {
                contentChannel.cancel(CancellationException("Missing request payload"))
                deferredResponse.cancel()
                respond(HttpStatusCode.BadRequest, "No request payload supplied")
                null
            }
            else -> deferredResponse.await()
        }
    }

private suspend fun PartData.readEvaluationRequestInto(evaluationRequest: CompletableDeferred<EvaluateRuleDefinitionsRequest>) {
    try {
        if (this is PartData.FormItem) {
            evaluationRequest.complete(Json.decodeFromString(value))
        }
    } finally {
        release()
    }
}
