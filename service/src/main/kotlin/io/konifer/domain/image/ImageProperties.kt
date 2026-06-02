package io.konifer.domain.image

import io.konifer.infrastructure.property.ConfigurationPropertyKeys.PathPropertyKeys.ImagePropertyKeys.LQIP
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ImageProperties(
    @SerialName(LQIP)
    val previews: Set<LQIPImplementation> = emptySet(),
) {
    companion object Factory {
        val default =
            ImageProperties(
                previews = setOf(),
            )
    }
}
