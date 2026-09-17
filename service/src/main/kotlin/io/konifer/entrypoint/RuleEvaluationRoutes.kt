package io.konifer.entrypoint

import io.konifer.application.usecase.evaluate.EvaluateRuleDefinitionUseCase
import io.konifer.common.http.EvaluateRuleDefinitionsRequest
import io.konifer.common.http.EvaluateRuleDefinitionsResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.contentType
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject

const val RULE_EVALUATIONS_PATH = "/rule-evaluations"

private val logger = KtorSimpleLogger("io.konifer.entrypoint.RuleEvaluationRoutes")

fun Application.configureRuleEvaluationRouting() {
    logger.info("Evaluating rule evaluation routes")
    val evaluateRuleDefinitionUseCase by inject<EvaluateRuleDefinitionUseCase>()

    routing {
        route(RULE_EVALUATIONS_PATH) {
            post {
                call.evaluateRuleDefinitions(
                    evaluateRuleDefinitionUseCase = evaluateRuleDefinitionUseCase,
                )
            }
        }
    }
}

private suspend fun RoutingCall.evaluateRuleDefinitions(evaluateRuleDefinitionUseCase: EvaluateRuleDefinitionUseCase) {
    when (request.contentType().withoutParameters()) {
        ContentType.MultiPart.FormData -> {
            logger.info("Received multipart request to evaluate rule definitions")
            evaluateMultipartRuleDefinitions(
                evaluateRuleDefinitionUseCase = evaluateRuleDefinitionUseCase,
            ).let { response ->
                respond(HttpStatusCode.OK, response)
            }
        }
        ContentType.Application.Json -> {
            logger.info("Received json request to evaluate rule definitions")
            val payload = receive(EvaluateRuleDefinitionsRequest::class)
            val response =
                evaluateRuleDefinitionUseCase.handleFromExternalSource(
                    request = payload,
                )
            respond(HttpStatusCode.OK, response)
        }
        else -> respond(HttpStatusCode.UnsupportedMediaType)
    }
}

private suspend fun RoutingCall.evaluateMultipartRuleDefinitions(
    evaluateRuleDefinitionUseCase: EvaluateRuleDefinitionUseCase,
): EvaluateRuleDefinitionsResponse =
    receiveMultipartUpload { Json.decodeFromString<EvaluateRuleDefinitionsRequest>(it) }.use { upload ->
        when {
            upload.duplicateAssetReceived -> {
                throw IllegalArgumentException("Multiple asset payloads supplied")
            }
            upload.duplicateMetadataReceived -> {
                throw IllegalArgumentException("Multiple metadata payloads supplied")
            }
            !upload.request.isCompleted -> {
                throw IllegalArgumentException("No asset metadata supplied")
            }
            upload.assetContainer == null -> {
                throw IllegalArgumentException("No asset payload supplied")
            }
            else ->
                evaluateRuleDefinitionUseCase.handleFromUpload(
                    deferredRequest = upload.request,
                    multiPartContainer = upload.assetContainer,
                )
        }
    }
