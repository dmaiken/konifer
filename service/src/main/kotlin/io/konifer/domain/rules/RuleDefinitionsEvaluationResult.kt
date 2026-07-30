package io.konifer.domain.rules

data class RuleDefinitionsEvaluationResult(
    val results: List<RuleEvaluationResult>,
) {
    companion object Factory {
        val none = RuleDefinitionsEvaluationResult(results = emptyList())
    }
}

data class RuleEvaluationResult(
    val ruleDefinition: RuleDefinition,
    val evaluationScore: EvaluationScore,
    val promptScores: Map<RulePrompt, Double>,
)

data class EvaluationScore(
    val score: Double,
    val matched: Boolean,
)
