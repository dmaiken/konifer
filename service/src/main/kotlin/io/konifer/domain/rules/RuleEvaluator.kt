package io.konifer.domain.rules

interface RuleEvaluator {

    suspend fun evaluate(ruleDefinitions: List<RuleDefinition>, input: RuleEvaluationInput): List<RuleEvaluationResult>
}
