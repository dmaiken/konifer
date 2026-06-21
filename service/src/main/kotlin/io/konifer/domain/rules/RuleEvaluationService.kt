package io.konifer.domain.rules

import io.konifer.domain.ports.RuleDefinitionRepository
import java.nio.file.Path

class RuleEvaluationService(
    private val ruleDefinitionRepository: RuleDefinitionRepository,
    private val ruleEvaluator: RuleEvaluator,
) {
    suspend fun evaluate(
        rules: List<RuleName>,
        path: Path,
    ) {
        val ruleDefinitions = rules.map { ruleDefinitionRepository.fetch(it) }

        ruleEvaluator.evaluate(
            ruleDefinitions = ruleDefinitions,
            input = RuleEvaluationInput(path),
        )
    }
}
