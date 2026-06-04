package io.konifer.infrastructure.variant.profile

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigObject
import io.konifer.domain.context.RequestedTransformation
import io.konifer.domain.ports.VariantProfileRepository
import io.konifer.infrastructure.property.ConfigurationPropertyKeys
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.hocon.Hocon
import kotlinx.serialization.hocon.decodeFromConfig

class ConfigurationVariantProfileRepository(
    applicationConfig: Config,
) : VariantProfileRepository {
    private val profiles = populateProfiles(applicationConfig)

    override fun fetch(profileName: String): RequestedTransformation =
        profiles[profileName.lowercase()]
            ?: throw IllegalArgumentException("Variant profile: '$profileName' not found")

    @OptIn(ExperimentalSerializationApi::class)
    private fun populateProfiles(applicationConfig: Config): Map<String, RequestedTransformation> =
        try {
            buildMap {
                applicationConfig
                    .takeIf { it.hasPath(ConfigurationPropertyKeys.VARIANT_PROFILES) }
                    ?.getConfig(ConfigurationPropertyKeys.VARIANT_PROFILES)
                    ?.root()
                    ?.forEach { (profileName, profileDefinition) ->
                        if (!isUrlSafe(profileName)) {
                            throw IllegalArgumentException("Profile name: '$profileName' is not valid")
                        }
                        if (contains(profileName)) {
                            throw IllegalArgumentException("Profile name: '$profileName' already exists")
                        }
                        val rootNodeConfig =
                            (profileDefinition as? ConfigObject)?.toConfig()
                                ?: throw IllegalArgumentException("Configuration for variant profile '$profileName' must be an object")

                        put(profileName, Hocon.decodeFromConfig<RequestedTransformation>(rootNodeConfig))
                    }
            }
        } catch (e: ConfigException) {
            throw IllegalArgumentException("Failed to populate variant profiles: ${e.message}", e)
        }

    private fun isUrlSafe(input: String): Boolean = input.all { it.isLetterOrDigit() || it in "-._~" }
}
