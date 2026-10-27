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
        if (prompts.isEmpty()) {
            throw InvalidRuleDefinitionException("Rule prompts cannot be empty")
        }
        if (prompts.size > 100) {
            throw InvalidRuleDefinitionException("Cannot have more than 100 prompts per rule definition")
        }
        if (prompts.distinct().size != prompts.size) {
            throw InvalidRuleDefinitionException("Rule prompts must be distinct")
        }
    }
}

@JvmInline
value class RuleDefinitions(
    val definitions: List<RuleDefinition>,
) {
    init {
        if (definitions.isEmpty()) {
            throw InvalidRuleDefinitionException("At least one rule request is required")
        }
        if (definitions.size > 10) {
            throw InvalidRuleDefinitionException("Maximum of 10 rule definitions allowed per request")
        }
    }
}

@JvmInline
@Serializable
value class RuleDefinitionThreshold(
    val value: Double,
) {
    init {
        if (value !in 0.0..1.0) {
            throw InvalidRuleDefinitionException("Rule threshold must be between 0.0 and 1.0")
        }
    }
}

fun RuleDefinitionRequest.toRuleDefinition(): RuleDefinition =
    RuleDefinition(
        name = RuleName(name),
        prompts = prompts.map { RulePrompt(it) },
        threshold = RuleDefinitionThreshold(threshold),
    )

fun EvaluateRuleDefinitionsRequest.toRuleDefinitions(): RuleDefinitions =
    RuleDefinitions(
        definitions =
            definitions.map { definition ->
                definition.toRuleDefinition()
            },
    )
