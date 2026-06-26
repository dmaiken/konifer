package io.konifer.infrastructure.rules

import io.konifer.domain.rules.RuleDefinition
import io.konifer.domain.rules.RulesetEvaluationResult
import io.konifer.infrastructure.variant.ImageTensor

interface RuleEvaluator {

    fun evaluate(
        ruleDefinitions: List<RuleDefinition>,
        tensor: ImageTensor,
    ): RulesetEvaluationResult
}
