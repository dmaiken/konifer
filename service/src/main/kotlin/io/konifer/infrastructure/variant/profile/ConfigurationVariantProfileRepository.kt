package io.konifer.infrastructure.variant.profile

import io.konifer.domain.ports.VariantProfileRepository
import io.konifer.infrastructure.property.ConfigurationPropertyKeys
import io.konifer.infrastructure.tryGetConfigList
import io.konifer.service.context.RequestedTransformation
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.tryGetString

class ConfigurationVariantProfileRepository(
    applicationConfig: ApplicationConfig,
) : VariantProfileRepository {
    private val profiles = populateProfiles(applicationConfig)

    override fun fetch(profileName: String): RequestedTransformation =
        profiles[profileName.lowercase()]
            ?: throw IllegalArgumentException("Variant profile: '$profileName' not found")

    private fun populateProfiles(applicationConfig: ApplicationConfig): Map<String, RequestedTransformation> =
        buildMap {
            applicationConfig
                .tryGetConfigList(
                    path = ConfigurationPropertyKeys.VARIANT_PROFILES,
                ).forEach { profileConfig ->
                    val profileName =
                        profileConfig.tryGetString(
                            key = ConfigurationPropertyKeys.PathPropertyKeys.VariantProfilePropertyKeys.NAME,
                        ) ?: throw IllegalArgumentException("All variant profiles must have a name")
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
