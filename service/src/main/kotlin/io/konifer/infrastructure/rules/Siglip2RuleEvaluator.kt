package io.konifer.infrastructure.rules

import io.konifer.domain.rules.RuleDefinition
import io.konifer.domain.rules.RuleEvaluationInput
import io.konifer.domain.rules.RuleEvaluationResult
import io.konifer.domain.rules.RuleEvaluator
import io.ktor.util.logging.KtorSimpleLogger

class Siglip2RuleEvaluator(
    private val tokenizer: Siglip2Tokenizer,
) : RuleEvaluator {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    override suspend fun evaluate(
        ruleDefinitions: List<RuleDefinition>,
        input: RuleEvaluationInput,
    ): List<RuleEvaluationResult> {
        // TODO
        return emptyList()
    }
}
