package io.konifer.infrastructure.rules

import io.konifer.domain.rules.RuleDefinition
import io.konifer.domain.rules.RuleDefinitionsEvaluationResult
import io.konifer.infrastructure.vips.processor.ImageTensor

interface RuleEvaluator {
    suspend fun evaluate(
        ruleDefinitions: List<RuleDefinition>,
        tensor: ImageTensor,
    ): RuleDefinitionsEvaluationResult
}
