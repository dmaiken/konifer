package io.konifer.domain.rules

data class RulesetEvaluationResult(
    val results: List<RuleEvaluationResult>,
) {
    companion object Factory {
        val none = RulesetEvaluationResult(results = emptyList())
    }
}

data class RuleEvaluationResult(
    val ruleDefinition: RuleDefinition,
    val evaluationScore: EvaluationScore,
    val promptScores: Map<String, Double>,
)

data class EvaluationScore(
    val score: Double,
    val matched: Boolean,
)
