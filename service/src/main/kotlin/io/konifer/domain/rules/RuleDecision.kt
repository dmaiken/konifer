package io.konifer.domain.rules

import io.konifer.domain.asset.AssetLabels
import io.konifer.domain.asset.merge
import io.konifer.domain.rules.upload.DefaultRuleAction
import io.konifer.domain.rules.upload.UploadRule

data class RuleDecision(
    val accept: Boolean,
    val violationResponses: List<RuleViolationResponse>,
    val labels: AssetLabels,
)

fun DefaultRuleAction.toDecision(labelRules: List<UploadRule>): RuleDecision =
    when (this) {
        DefaultRuleAction.ACCEPT -> RuleDecision(
            accept = true,
            violationResponses = emptyList(),
            labels = labelRules.map { it.labels }.merge(),
        )
        DefaultRuleAction.REJECT -> RuleDecision(
            accept = false,
            violationResponses = emptyList(),
            labels = labelRules.map { it.labels }.merge(),
        )
    }
