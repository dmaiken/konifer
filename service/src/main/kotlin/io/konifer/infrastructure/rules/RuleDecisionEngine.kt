package io.konifer.infrastructure.rules

import io.konifer.domain.rules.RuleDecision
import io.konifer.domain.rules.RuleDefinition
import io.konifer.domain.rules.RuleViolationResponse
import io.konifer.domain.rules.RulesetEvaluationResult
import io.konifer.domain.rules.upload.DefaultRuleAction
import io.konifer.domain.rules.upload.UploadRule
import kotlin.collections.iterator

object RuleDecisionEngine {
    /**
     * Decisions the [evaluationResult] based on the [default] and [definitionsByRule].
     *
     * If [default] is [DefaultRuleAction.ACCEPT], then the [evaluationResult] must have a match to reject.
     * If [default] is [DefaultRuleAction.REJECT], then the [evaluationResult] must have a match to accept.
     */
    fun makeDecision(
        default: DefaultRuleAction,
        definitionsByRule: Map<UploadRule, RuleDefinition>,
        evaluationResult: RulesetEvaluationResult,
    ): RuleDecision {
        val evaluationResultsByDefinition = evaluationResult.results.associateBy { it.ruleDefinition }

        val matchedRules = mutableListOf<RuleViolationResponse?>()
        for ((rule, ruleDefinition) in definitionsByRule) {
            val result =
                evaluationResultsByDefinition[ruleDefinition]
                    ?: throw IllegalStateException("Missing evaluation result for: ${rule.rule}")

            if (result.matched) {
                matchedRules.add(rule.violationResponse)
            }
        }

        return when (default) {
            DefaultRuleAction.ACCEPT -> {
                // Matched means we reject
                RuleDecision(
                    accept = matchedRules.isEmpty(),
                    violationResponses = matchedRules.filterNotNull(),
                )
            }
            DefaultRuleAction.REJECT -> {
                // Matched means we accept
                RuleDecision(
                    accept = matchedRules.isNotEmpty(),
                    violationResponses = emptyList(),
                )
            }
        }
    }
}
