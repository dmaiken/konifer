package io.konifer.infrastructure.rules

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigObject
import io.konifer.domain.ports.RuleDefinitionRepository
import io.konifer.domain.rules.RuleDefinition
import io.konifer.domain.rules.RuleName
import io.konifer.infrastructure.property.ConfigurationPropertyKeys
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.hocon.Hocon
import kotlinx.serialization.hocon.decodeFromConfig

class ConfigurationRuleDefinitionRepository(
    applicationConfig: Config,
) : RuleDefinitionRepository {
    private val rules = populateRules(applicationConfig)

    override fun fetch(ruleName: RuleName): RuleDefinition =
        rules[ruleName.value.lowercase()]
            ?: throw IllegalArgumentException("Rule: '$ruleName' not found")

    @OptIn(ExperimentalSerializationApi::class)
    private fun populateRules(applicationConfig: Config): Map<String, RuleDefinition> =
        try {
            buildMap {
                applicationConfig
                    .takeIf { it.hasPath(ConfigurationPropertyKeys.RULE_DEFINITIONS) }
                    ?.getConfig(ConfigurationPropertyKeys.RULE_DEFINITIONS)
                    ?.root()
                    ?.forEach { (ruleName, ruleDefinition) ->
                        if (contains(ruleName)) {
                            throw IllegalArgumentException("Rule name: '$ruleName' already exists")
                        }
                        val rootNodeConfig =
                            (ruleDefinition as? ConfigObject)?.toConfig()
                                ?: throw IllegalArgumentException("Configuration for rule '$ruleName' must be an object")

                        put(ruleName, Hocon.decodeFromConfig<RuleDefinition>(rootNodeConfig))
                    }
            }
        } catch (e: ConfigException) {
            throw IllegalArgumentException("Failed to populate rules: ${e.message}", e)
        }
}
