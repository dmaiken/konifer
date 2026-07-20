package io.konifer.application.usecase.evaluate

import io.konifer.common.http.EvaluateRuleDefinitionsRequest
import io.konifer.common.http.EvaluateRuleDefinitionsResponse
import io.konifer.common.http.EvaluatedPromptResponse
import io.konifer.common.http.EvaluatedRuleDefinitionResponse
import io.konifer.common.image.ImageFormat
import io.konifer.domain.asset.AssetDataContainer
import io.konifer.domain.image.fromMimeType
import io.konifer.domain.ports.AssetContainerFactory
import io.konifer.domain.ports.MimeTypeDetector
import io.konifer.domain.ports.RuleEvaluationProcessor
import io.konifer.domain.rules.RuleEvaluationResult
import io.konifer.domain.rules.toRuleDefinitions
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.CompletableDeferred

class EvaluateRuleDefinitionUseCase(
    private val assetStreamContainerFactory: AssetContainerFactory,
    private val mimeTypeDetector: MimeTypeDetector,
    private val ruleEvaluationProcessor: RuleEvaluationProcessor,
) {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    suspend fun handleFromUpload(
        deferredRequest: CompletableDeferred<EvaluateRuleDefinitionsRequest>,
        multiPartContainer: AssetDataContainer,
    ): EvaluateRuleDefinitionsResponse =
        handle(
            request = deferredRequest.await(),
            assetDataContainer = multiPartContainer,
        )

    suspend fun handleFromUrl(request: EvaluateRuleDefinitionsRequest): EvaluateRuleDefinitionsResponse =
        handle(
            request = request,
            assetDataContainer = assetStreamContainerFactory.fromUrlSource(request.url),
        )

    private suspend fun handle(
        assetDataContainer: AssetDataContainer,
        request: EvaluateRuleDefinitionsRequest,
    ): EvaluateRuleDefinitionsResponse {
        assetDataContainer.use { container ->
            val ruleDefinitions = request.toRuleDefinitions().associateBy { it.name }
            logger.info("Evaluating rule definitions for rule definitions: ${ruleDefinitions.keys}")

            // Ensure content is a supported content type
            val contentType = mimeTypeDetector.detect(assetDataContainer.peek(1024))
            val sourceFormat = ImageFormat.fromMimeType(contentType)

            assetDataContainer.toTemporaryFile(sourceFormat.extension)
            val result =
                ruleEvaluationProcessor
                    .evaluate(
                        sourceFormat = sourceFormat,
                        ruleDefinitions = ruleDefinitions.values.toList(),
                        source = container.getTemporaryFile(),
                    ).await()

            return result.results
                .map { it.toEvaluatedResponse() }
                .let { EvaluateRuleDefinitionsResponse(results = it) }
        }
    }

    private fun RuleEvaluationResult.toEvaluatedResponse(): EvaluatedRuleDefinitionResponse =
        EvaluatedRuleDefinitionResponse(
            name = ruleDefinition.name.value,
            threshold = ruleDefinition.threshold.value,
            score = evaluationScore.score,
            matched = evaluationScore.matched,
            promptScores =
                ruleDefinition.prompts.map { prompt ->
                    EvaluatedPromptResponse(
                        prompt = prompt.prompt,
                        score =
                            checkNotNull(promptScores[prompt.prompt]) {
                                "Missing prompt score for '${prompt.prompt}' in rule '${ruleDefinition.name.value}'"
                            },
                    )
                },
        )
}
