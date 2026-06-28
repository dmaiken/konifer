package io.konifer.domain.rules

import io.konifer.domain.rules.upload.DefaultRuleAction

data class RuleDecision(
    val accept: Boolean,
    val violationResponses: List<RuleViolationResponse>,
) {
    companion object Factory {
        val accept = RuleDecision(accept = true, violationResponses = emptyList())
        val reject = RuleDecision(accept = false, violationResponses = emptyList())
    }
}

fun DefaultRuleAction.toDecision(): RuleDecision =
    when (this) {
        DefaultRuleAction.ACCEPT -> RuleDecision.accept
        DefaultRuleAction.REJECT -> RuleDecision.reject
    }
