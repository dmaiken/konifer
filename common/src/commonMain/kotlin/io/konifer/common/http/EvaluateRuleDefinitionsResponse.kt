package io.konifer.common.http

import kotlinx.serialization.Serializable

@Serializable
data class EvaluateRuleDefinitionsResponse(
    val results: List<EvaluatedRuleDefinitionResponse>,
)

@Serializable
data class EvaluatedRuleDefinitionResponse(
    val name: String,
    val threshold: Double,
    val score: Double,
    val matched: Boolean,
    val promptScores: List<EvaluatedPromptResponse>,
)

@Serializable
data class EvaluatedPromptResponse(
    val prompt: String,
    val score: Double,
)
