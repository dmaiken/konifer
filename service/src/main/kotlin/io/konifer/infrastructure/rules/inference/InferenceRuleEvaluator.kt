package io.konifer.infrastructure.rules.inference

import io.konifer.domain.rules.RuleDefinition
import io.konifer.domain.rules.RuleEvaluationResult
import io.konifer.domain.rules.RulesetEvaluationResult
import io.konifer.infrastructure.rules.inference.embedding.ContentEmbeddingService
import io.konifer.infrastructure.rules.inference.embedding.RulePromptEmbeddingService
import io.konifer.infrastructure.rules.RuleEvaluator
import io.konifer.infrastructure.variant.ImageTensor
import io.ktor.util.logging.KtorSimpleLogger

class InferenceRuleEvaluator(
    private val rulePromptEmbeddingService: RulePromptEmbeddingService,
    private val contentEmbeddingService: ContentEmbeddingService,
    private val similarityScorer: SimilarityScorer = Siglip2LogitSimilarityScorer(),
) : RuleEvaluator {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    override fun evaluate(
        ruleDefinitions: List<RuleDefinition>,
        tensor: ImageTensor,
    ): RulesetEvaluationResult {
        if (ruleDefinitions.isEmpty()) return RulesetEvaluationResult.empty
        // Generate embedding for input

        val contentEmbedding = contentEmbeddingService.generateEmbeddings(tensor)

        return ruleDefinitions
            .map { ruleDefinition ->
                val promptEmbedding = rulePromptEmbeddingService.generateEmbeddings(ruleDefinition.prompt)
                val score =
                    similarityScorer.score(
                        promptEmbedding = promptEmbedding,
                        contentEmbedding = contentEmbedding,
                    )

                RuleEvaluationResult(
                    ruleDefinition = ruleDefinition,
                    score = score,
                    matched = score >= ruleDefinition.threshold.value,
                )
            }.let {
                logger.info("Evaluating rules resulted in: $it")
                RulesetEvaluationResult(it)
            }
    }
}
