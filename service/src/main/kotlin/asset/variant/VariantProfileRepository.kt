package io.asset.variant

import image.model.RequestedImageAttributes
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.tryGetString
import io.properties.ConfigurationProperties.PathConfigurationProperties.VARIANT_PROFILES
import io.properties.ConfigurationProperties.PathConfigurationProperties.VariantProfilePropertyKeys.NAME

class VariantProfileRepository(
    applicationConfig: ApplicationConfig,
) {
    private val profiles = populateProfiles(applicationConfig)

    fun fetch(profileName: String): RequestedImageAttributes =
        profiles[profileName.lowercase()]
            ?: throw IllegalArgumentException("Variant profile: '$profileName' not found")

    private fun populateProfiles(applicationConfig: ApplicationConfig): Map<String, RequestedImageAttributes> =
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

                put(profileName, RequestedImageAttributes.create(profileConfig))
            }
        }

    private fun isUrlSafe(input: String): Boolean {
        return input.all { it.isLetterOrDigit() || it in "-._~" }
    }
}
