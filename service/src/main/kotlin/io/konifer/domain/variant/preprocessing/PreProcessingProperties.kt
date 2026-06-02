package io.konifer.domain.variant.preprocessing

import kotlinx.serialization.Serializable

@Serializable
data class PreProcessingProperties(
    val enabled: Boolean = false,
    val image: ImagePreProcessingProperties = ImagePreProcessingProperties.default,
) {
    companion object Factory {
        val default =
            PreProcessingProperties(
                enabled = false,
                image = ImagePreProcessingProperties.default,
            )
    }
}
