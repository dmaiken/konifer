package io.konifer.domain.rules

import io.konifer.domain.asset.AssetLabels
import io.konifer.domain.rules.upload.DefaultRuleAction

data class RuleDecision(
    val accept: Boolean,
    val violationResponses: List<RuleViolationResponse>,
    val labels: AssetLabels,
)

fun DefaultRuleAction.toDecision(): RuleDecision =
    when (this) {
        DefaultRuleAction.ACCEPT ->
            RuleDecision(
                accept = true,
                violationResponses = emptyList(),
                labels = AssetLabels.empty,
            )
        DefaultRuleAction.REJECT ->
            RuleDecision(
                accept = false,
                violationResponses = emptyList(),
                labels = AssetLabels.empty,
            )
    }
