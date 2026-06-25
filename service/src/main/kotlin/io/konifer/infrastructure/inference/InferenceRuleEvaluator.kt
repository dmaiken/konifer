package io.konifer.infrastructure.inference

import io.konifer.domain.rules.RuleDefinition
import io.konifer.infrastructure.rules.RuleEvaluationInput
import io.konifer.domain.rules.RuleEvaluationResult
import io.konifer.infrastructure.inference.embedding.RulePromptEmbeddingService
import io.konifer.infrastructure.rules.RuleEvaluator
import io.ktor.util.logging.KtorSimpleLogger

class InferenceRuleEvaluator(
    private val rulePromptEmbeddingService: RulePromptEmbeddingService,
) : RuleEvaluator {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    override suspend fun evaluate(
        ruleDefinitions: List<RuleDefinition>,
        input: RuleEvaluationInput,
    ): List<RuleEvaluationResult> {
        if (ruleDefinitions.isEmpty()) return emptyList()
        // Generate embedding for input

        ruleDefinitions.forEach { ruleDefinition ->
        }
        return emptyList()
    }
}
