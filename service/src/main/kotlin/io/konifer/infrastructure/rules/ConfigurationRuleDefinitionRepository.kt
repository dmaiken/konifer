package io.konifer.infrastructure.rules

import io.konifer.domain.ports.RuleDefinitionRepository
import io.konifer.domain.rules.RuleDefinition
import io.konifer.domain.rules.RuleName

class ConfigurationRuleDefinitionRepository(
    private val ruleDefinitions: Map<String, RuleDefinition>,
) : RuleDefinitionRepository {
    override fun fetch(ruleName: RuleName): RuleDefinition =
        ruleDefinitions[ruleName.value]
            ?: throw IllegalArgumentException("Rule with name: '${ruleName.value}' not found")
}
