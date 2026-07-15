package io.konifer.domain.rules.upload

import io.konifer.domain.rules.RuleName
import io.konifer.domain.rules.RuleViolationResponse
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.PathPropertyKeys.UploadRulesetPropertyKeys
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UploadRuleset(
    @SerialName(UploadRulesetPropertyKeys.DEFAULT)
    val default: DefaultRuleAction = DefaultRuleAction.default,
    @SerialName(UploadRulesetPropertyKeys.ACCEPT_RULES)
    val acceptRules: List<UploadRule> = emptyList(),
    @SerialName(UploadRulesetPropertyKeys.REJECT_RULES)
    val rejectRules: List<UploadRule> = emptyList(),
    // Label rules later
) {
    companion object Factory {
        val default = UploadRuleset()
    }

    init {
        require(acceptRules.none { it in rejectRules } || rejectRules.none { it in acceptRules }) {
            "Upload rulesets cannot contain the same rule in both ${UploadRulesetPropertyKeys.ACCEPT_RULES} and ${UploadRulesetPropertyKeys.REJECT_RULES}"
        }
    }

    fun requiresEvaluationBeyondDefault(): Boolean =
        when (default) {
            DefaultRuleAction.ACCEPT -> rejectRules.isNotEmpty()
            DefaultRuleAction.REJECT -> acceptRules.isNotEmpty()
        }
}

@Serializable
data class UploadRule(
    @SerialName(UploadRulesetPropertyKeys.RulePropertyKeys.RULE)
    val rule: RuleName,
    @SerialName(UploadRulesetPropertyKeys.RulePropertyKeys.VIOLATION_RESPONSE)
    val violationResponse: RuleViolationResponse? = null,
)
