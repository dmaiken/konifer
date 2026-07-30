package io.konifer.infrastructure.rules

import io.konifer.domain.ports.RuleDefinitionRepository
import io.konifer.domain.rules.RuleDefinition
import io.konifer.domain.rules.RuleName

class ConfigurationRuleDefinitionRepository(
    ruleDefinitions: List<RuleDefinition>,
) : RuleDefinitionRepository {
    private val ruleDefinitionsByName =
        ruleDefinitions
            .groupBy { it.name.value }
            .onEach { (name, definitions) ->
                require(definitions.size == 1) { "Rule name: '$name' already exists" }
            }.mapValues { it.value.single() }

    override fun fetch(ruleName: RuleName): RuleDefinition =
        ruleDefinitionsByName[ruleName.value]
            ?: throw IllegalArgumentException("Rule with name: '${ruleName.value}' not found")
}
