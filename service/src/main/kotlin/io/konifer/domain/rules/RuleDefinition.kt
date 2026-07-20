package io.konifer.domain.rules

import io.konifer.common.http.EvaluateRuleDefinitionsRequest
import io.konifer.common.http.RuleDefinitionRequest
import kotlinx.serialization.Serializable

data class RuleDefinition(
    val name: RuleName,
    val prompts: List<RulePrompt>,
    val threshold: RuleDefinitionThreshold,
) {
    init {
        require(prompts.isNotEmpty()) {
            "Rule prompts cannot be empty"
        }
        require(prompts.size <= 100) {
            "Cannot have more than 100 prompts per rule definition"
        }
        require(prompts.distinct().size == prompts.size) {
            "Rule prompts must be distinct"
        }
    }
}

@JvmInline
@Serializable
value class RuleDefinitionThreshold(
    val value: Double,
) {
    init {
        require(value in 0.0..1.0) { "Rule threshold must be between 0.0 and 1.0" }
    }
}

fun RuleDefinitionRequest.toRuleDefinition(): RuleDefinition =
    RuleDefinition(
        name = RuleName(name),
        prompts = prompts.map { RulePrompt(it) },
        threshold = RuleDefinitionThreshold(threshold),
    )

fun EvaluateRuleDefinitionsRequest.toRuleDefinitions(): List<RuleDefinition> =
    definitions.map { definition ->
        definition.toRuleDefinition()
    }
