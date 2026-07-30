package io.konifer.common.http

import kotlinx.serialization.Serializable

@Serializable
data class EvaluateRuleDefinitionsRequest(
    val url: String? = null,
    val definitions: List<RuleDefinitionRequest>,
) {
    init {
        require(definitions.isNotEmpty()) { "At least one rule request is required" }
        require(definitions.size <= 10) { "Maximum of 10 rule definitions allowed per request" }
    }
}

@Serializable
data class RuleDefinitionRequest(
    val name: String,
    val prompts: List<String>,
    val threshold: Double,
)
