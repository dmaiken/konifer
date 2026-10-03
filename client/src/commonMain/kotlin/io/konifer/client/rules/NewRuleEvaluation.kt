package io.konifer.client.rules

import io.konifer.client.KoniferResult
import io.konifer.client.internal.RequestInfrastructure
import io.konifer.common.http.EvaluateRuleDefinitionsResponse
import io.konifer.common.http.RuleDefinitionRequest

/**
 * Immutable rule evaluation with a selected image source and accumulated definitions.
 *
 * Definition modifiers return independent evaluations and snapshot each definition's prompt list.
 */
class NewRuleEvaluation internal constructor(
    private val infra: RequestInfrastructure,
    private val source: RuleEvaluationSource,
    private val definitions: List<RuleDefinitionRequest> = emptyList(),
) {
    /** Appends a snapshot of [definition] to this evaluation. */
    fun withDefinition(definition: RuleDefinitionRequest): NewRuleEvaluation =
        NewRuleEvaluation(
            infra = infra,
            source = source,
            definitions = definitions + definition.copy(prompts = definition.prompts.toList()),
        )

    /** Appends snapshots of [definitions] to this evaluation. */
    fun withDefinitions(definitions: List<RuleDefinitionRequest>): NewRuleEvaluation =
        NewRuleEvaluation(
            infra = infra,
            source = source,
            definitions = this.definitions + definitions.map { it.copy(prompts = it.prompts.toList()) },
        )

    /** Evaluates the accumulated definitions against the selected image. */
    suspend fun evaluate(): KoniferResult<EvaluateRuleDefinitionsResponse> =
        evaluateRules(
            infra = infra,
            source = source,
            definitions = definitions,
        )
}
