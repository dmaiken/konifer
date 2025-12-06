package io.direkt.asset.variant

import io.direkt.domain.image.RequestedTransformation
import io.direkt.properties.ConfigurationProperties.PathConfigurationProperties.VARIANT_PROFILES
import io.direkt.properties.ConfigurationProperties.PathConfigurationProperties.VariantProfilePropertyKeys.NAME
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.tryGetString

class VariantProfileRepository(
    applicationConfig: ApplicationConfig,
) {
    private val profiles = populateProfiles(applicationConfig)

    fun fetch(profileName: String): RequestedTransformation =
        profiles[profileName.lowercase()]
            ?: throw IllegalArgumentException("Variant profile: '$profileName' not found")

    private fun populateProfiles(applicationConfig: ApplicationConfig): Map<String, RequestedTransformation> =
        buildMap {
            applicationConfig.configList(VARIANT_PROFILES).forEach { profileConfig ->
                val profileName =
                    profileConfig.tryGetString(NAME)
                        ?: throw IllegalArgumentException("All variant profiles must have a name")
                if (!isUrlSafe(profileName)) {
                    throw IllegalArgumentException("Profile name: '$profileName' is not valid")
                }
                if (contains(profileName)) {
                    throw IllegalArgumentException("Profile name: '$profileName' already exists")
                }

                put(profileName, RequestedTransformation.create(profileConfig))
            }
        }

    private fun isUrlSafe(input: String): Boolean = input.all { it.isLetterOrDigit() || it in "-._~" }
}
