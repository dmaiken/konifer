package io.konifer.domain.rules

import io.konifer.domain.asset.AssetLabels

sealed interface UploadRuleDecision {
    val ruleDefinitionsEvaluationResult: RuleDefinitionsEvaluationResult

    class Success(
        override val ruleDefinitionsEvaluationResult: RuleDefinitionsEvaluationResult,
        val labels: AssetLabels,
    ) : UploadRuleDecision

    class Rejected(
        override val ruleDefinitionsEvaluationResult: RuleDefinitionsEvaluationResult,
        val violationResponses: List<RuleViolationResponse>,
    ) : UploadRuleDecision
}
