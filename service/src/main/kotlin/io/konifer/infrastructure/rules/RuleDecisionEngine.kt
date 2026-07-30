package io.konifer.infrastructure.rules

import io.konifer.domain.asset.AssetLabels
import io.konifer.domain.asset.merge
import io.konifer.domain.rules.RuleDecision
import io.konifer.domain.rules.RuleDefinitionsEvaluationResult
import io.konifer.domain.rules.RuleViolationResponse
import io.konifer.domain.rules.upload.DefaultRuleAction
import io.konifer.domain.rules.upload.UploadRule
import io.konifer.domain.rules.upload.UploadRuleset

object RuleDecisionEngine {
    fun makeDecision(
        uploadRuleset: UploadRuleset,
        evaluationResult: RuleDefinitionsEvaluationResult,
    ): RuleDecision {
        val evaluationResultsByRuleName = evaluationResult.results.associateBy { it.ruleDefinition.name }

        val matchedAcceptanceRules = mutableListOf<RuleViolationResponse?>()
        val matchedLabelRules = mutableListOf<AssetLabels>()
        uploadRuleset
            .rulesToDecision()
            .forEach { rule ->
                val result =
                    evaluationResultsByRuleName[rule.rule]
                        ?: throw IllegalStateException("Missing evaluation result for: ${rule.rule}")

                if (result.evaluationScore.matched) {
                    when (rule) {
                        in uploadRuleset.acceptRules -> matchedAcceptanceRules.add(rule.violationResponse)
                        in uploadRuleset.rejectRules -> matchedAcceptanceRules.add(rule.violationResponse)
                        in uploadRuleset.labelRules -> matchedLabelRules.add(rule.labels)
                    }
                }
            }

        return when (uploadRuleset.default) {
            DefaultRuleAction.ACCEPT -> {
                // Matched means we reject
                RuleDecision(
                    accept = matchedAcceptanceRules.isEmpty(),
                    violationResponses = matchedAcceptanceRules.filterNotNull(),
                    labels = matchedLabelRules.merge(),
                )
            }
            DefaultRuleAction.REJECT -> {
                // Matched means we accept
                RuleDecision(
                    accept = matchedAcceptanceRules.isNotEmpty(),
                    violationResponses = emptyList(),
                    labels = matchedLabelRules.merge(),
                )
            }
        }
    }

    private fun UploadRuleset.rulesToDecision(): List<UploadRule> =
        when (default) {
            DefaultRuleAction.ACCEPT -> rejectRules
            DefaultRuleAction.REJECT -> acceptRules
        } + labelRules
}
