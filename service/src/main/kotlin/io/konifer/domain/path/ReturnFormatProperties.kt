package io.konifer.domain.path

import io.konifer.infrastructure.property.ConfigurationPropertyKeys
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ReturnFormatProperties(
    @SerialName(ConfigurationPropertyKeys.PathPropertyKeys.ReturnFormatPropertyKeys.REDIRECT)
    val redirect: RedirectProperties = RedirectProperties.default,
) {
    companion object Factory {
        val default = ReturnFormatProperties()
    }
}
