package io.konifer.domain.rules

data class RuleEvaluationResult(
    val ruleDefinition: RuleDefinition,
    val resultThreshold: Double,
)
