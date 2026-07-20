package io.konifer.infrastructure.rules.inference

import io.konifer.domain.rules.EvaluationScore
import io.konifer.domain.rules.RuleDefinition
import io.konifer.domain.rules.RuleDefinitionsEvaluationResult
import io.konifer.domain.rules.RuleEvaluationResult
import io.konifer.infrastructure.rules.RuleEvaluator
import io.konifer.infrastructure.rules.inference.embedding.ContentEmbeddingService
import io.konifer.infrastructure.rules.inference.embedding.RulePromptEmbeddingService
import io.konifer.infrastructure.vips.processor.ImageTensor
import io.ktor.util.logging.KtorSimpleLogger
import kotlin.math.max

class InferenceRuleEvaluator(
    private val rulePromptEmbeddingService: RulePromptEmbeddingService,
    private val contentEmbeddingService: ContentEmbeddingService,
    private val similarityScorer: SimilarityScorer,
) : RuleEvaluator {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    override fun evaluate(
        ruleDefinitions: List<RuleDefinition>,
        tensor: ImageTensor,
    ): RuleDefinitionsEvaluationResult {
        if (ruleDefinitions.isEmpty()) return RuleDefinitionsEvaluationResult.none

        val contentEmbedding = contentEmbeddingService.generateEmbeddings(tensor)

        return ruleDefinitions
            .map { ruleDefinition ->
                val promptScores = mutableMapOf<String, Double>()
                var score: Double = -1.0
                ruleDefinition.prompts.forEach { prompt ->
                    val embeddings = rulePromptEmbeddingService.generateEmbeddings(prompt)

                    val promptScore =
                        similarityScorer.score(
                            promptEmbedding = embeddings,
                            contentEmbedding = contentEmbedding,
                        )
                    promptScores[prompt] = promptScore
                    score = max(score, promptScore)
                }

                RuleEvaluationResult(
                    ruleDefinition = ruleDefinition,
                    evaluationScore =
                        EvaluationScore(
                            score = score,
                            matched = score >= ruleDefinition.threshold.value,
                        ),
                    promptScores = promptScores,
                )
            }.let {
                logger.info("Evaluating rules resulted in: $it")
                RuleDefinitionsEvaluationResult(it)
            }
    }
}
