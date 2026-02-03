package io.konifer.domain.image

import io.konifer.infrastructure.properties.ConfigurationPropertyKeys.PathPropertyKeys.ImagePropertyKeys.LQIP
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.tryGetStringList

data class ImageProperties(
    val previews: Set<LQIPImplementation>,
) {
    companion object Factory {
        val default =
            ImageProperties(
                previews = setOf(),
            )

        fun create(
            applicationConfig: ApplicationConfig?,
            parent: ImageProperties?,
        ): ImageProperties =
            ImageProperties(
                previews =
                    applicationConfig
                        ?.tryGetStringList(LQIP)
                        ?.map { LQIPImplementation.valueOf(it.uppercase()) }
                        ?.toSet() ?: parent?.previews ?: setOf(),
            )
    }
}
