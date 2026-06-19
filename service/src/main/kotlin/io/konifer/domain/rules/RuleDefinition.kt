package io.konifer.domain.rules

import io.konifer.common.serializer.LowercaseEnumSerializer
import io.konifer.infrastructure.property.ConfigurationPropertyKeys
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RuleDefinition(
    @SerialName(ConfigurationPropertyKeys.RuleDefinitionPropertyKeys.PROMPT)
    val prompt: String,
    @SerialName(ConfigurationPropertyKeys.RuleDefinitionPropertyKeys.ACTION)
    val action: RuleDefinitionAction,
    @SerialName(ConfigurationPropertyKeys.RuleDefinitionPropertyKeys.THRESHOLD)
    val threshold: RuleDefinitionThreshold,
)

@JvmInline
@Serializable
value class RuleDefinitionThreshold(val value: Double) {
    init {
        require(value in 0.0..1.0) { "Rule threshold must be between 0.0 and 1.0" }
    }
}

@Serializable(with = RuleDefinitionActionSerializer::class)
enum class RuleDefinitionAction {
    MATCH, REJECT
}

class RuleDefinitionActionSerializer : KSerializer<RuleDefinitionAction> by LowercaseEnumSerializer(RuleDefinitionAction.entries)
