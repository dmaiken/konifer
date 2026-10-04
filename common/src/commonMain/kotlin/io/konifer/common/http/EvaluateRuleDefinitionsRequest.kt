package io.konifer.common.http

import kotlinx.serialization.Serializable

@Serializable
data class EvaluateRuleDefinitionsRequest(
    @Deprecated("use source.http.url")
    val url: String? = null,
    val source: AssetSourceRequest = AssetSourceRequest(),
    val definitions: List<RuleDefinitionRequest>,
)

@Serializable
data class RuleDefinitionRequest(
    val name: String,
    val prompts: List<String>,
    val threshold: Double,
)
