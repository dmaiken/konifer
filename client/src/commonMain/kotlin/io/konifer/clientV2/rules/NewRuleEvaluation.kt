package io.konifer.clientV2.rules

import io.konifer.clientV2.KoniferV2Result
import io.konifer.clientV2.internal.RequestInfrastructure
import io.konifer.common.http.EvaluateRuleDefinitionsResponse
import io.konifer.common.http.RuleDefinitionRequest

class NewRuleEvaluation internal constructor(
    private val infra: RequestInfrastructure,
    private val source: RuleEvaluationSource,
    private val definitions: List<RuleDefinitionRequest> = emptyList(),
) {
    fun withDefinition(definition: RuleDefinitionRequest): NewRuleEvaluation =
        NewRuleEvaluation(
            infra = infra,
            source = source,
            definitions = definitions + definition.copy(prompts = definition.prompts.toList()),
        )

    fun withDefinitions(definitions: List<RuleDefinitionRequest>): NewRuleEvaluation =
        NewRuleEvaluation(
            infra = infra,
            source = source,
            definitions = this.definitions + definitions.map { it.copy(prompts = it.prompts.toList()) },
        )

    suspend fun evaluate(): KoniferV2Result<EvaluateRuleDefinitionsResponse> =
        evaluateRules(
            infra = infra,
            source = source,
            definitions = definitions,
        )
}
