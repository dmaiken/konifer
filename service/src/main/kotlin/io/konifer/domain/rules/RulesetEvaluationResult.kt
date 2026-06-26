package io.konifer.domain.rules

data class RulesetEvaluationResult(
    val results: List<RuleEvaluationResult>,
) {
    companion object Factory {
        val empty = RulesetEvaluationResult(results = emptyList())
    }
}

data class RuleEvaluationResult(
    val ruleDefinition: RuleDefinition,
    val score: Double,
    val matched: Boolean,
)
