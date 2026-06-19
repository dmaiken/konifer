package io.konifer.domain.ports

import io.konifer.domain.rules.RuleDefinition
import io.konifer.domain.rules.RuleName

interface RuleDefinitionRepository {

    fun fetch(ruleName: RuleName): RuleDefinition
}
