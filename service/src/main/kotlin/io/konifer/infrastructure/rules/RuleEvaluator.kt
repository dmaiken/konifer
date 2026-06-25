package io.konifer.infrastructure.rules

import io.konifer.domain.rules.RuleDefinition
import io.konifer.domain.rules.RuleEvaluationResult
import io.konifer.infrastructure.variant.ImageTensor
import io.konifer.infrastructure.variant.TensorTransformation

interface RuleEvaluator {

    fun evaluate(
        ruleDefinitions: List<RuleDefinition>,
        tensor: ImageTensor,
    ): List<RuleEvaluationResult>
}
