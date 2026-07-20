package io.konifer.infrastructure.rules

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigObject
import io.konifer.domain.rules.RuleDefinition
import io.konifer.domain.rules.RuleDefinitionThreshold
import io.konifer.domain.rules.RuleName
import io.konifer.infrastructure.property.ConfigurationPropertyKeys
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.hocon.Hocon
import kotlinx.serialization.hocon.decodeFromConfig
import kotlin.math.sqrt

fun FloatArray.l2Normalize(): FloatArray {
    var sum = 0.0
    for (value in this) {
        sum += value * value
    }

    val norm = sqrt(sum).toFloat()
    require(norm > 0f) { "Cannot normalize zero-length embedding" }

    return FloatArray(size) { index -> this[index] / norm }
}

@OptIn(ExperimentalSerializationApi::class)
fun Config.getRuleDefinitions(): List<RuleDefinition> =
    try {
        this@getRuleDefinitions
            .takeIf { it.hasPath(ConfigurationPropertyKeys.RULE_DEFINITIONS) }
            ?.getConfig(ConfigurationPropertyKeys.RULE_DEFINITIONS)
            ?.root()
            ?.map { (ruleName, ruleDefinition) ->
                val rootNodeConfig =
                    (ruleDefinition as? ConfigObject)?.toConfig()
                        ?: throw IllegalArgumentException("Configuration for rule '$ruleName' must be an object")
                val config = Hocon.decodeFromConfig<ConfiguredRuleDefinition>(rootNodeConfig)

                RuleDefinition(
                    name = RuleName(ruleName),
                    prompts = config.prompts,
                    threshold = config.threshold,
                )
            } ?: emptyList()
    } catch (e: ConfigException) {
        throw IllegalArgumentException("Failed to populate rules: ${e.message}", e)
    }

@Serializable
private data class ConfiguredRuleDefinition(
    @SerialName(ConfigurationPropertyKeys.RuleDefinitionPropertyKeys.PROMPTS)
    val prompts: List<String>,
    @SerialName(ConfigurationPropertyKeys.RuleDefinitionPropertyKeys.THRESHOLD)
    val threshold: RuleDefinitionThreshold,
)
