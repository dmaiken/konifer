package io.konifer.domain.rules

import io.konifer.infrastructure.property.ConfigurationPropertyKeys
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RuleDefinition(
    @SerialName(ConfigurationPropertyKeys.RuleDefinitionPropertyKeys.PROMPTS)
    val prompts: List<String>,
    @SerialName(ConfigurationPropertyKeys.RuleDefinitionPropertyKeys.THRESHOLD)
    val threshold: RuleDefinitionThreshold,
) {
    init {
        require(prompts.isNotEmpty()) {
            "Rule prompts cannot be empty"
        }
        require(prompts.size <= 100) {
            "Cannot have more than 100 prompts per rule definition"
        }
    }
}

@JvmInline
@Serializable
value class RuleDefinitionThreshold(
    val value: Double,
) {
    init {
        require(value in 0.0..1.0) { "Rule threshold must be between 0.0 and 1.0" }
    }
}
