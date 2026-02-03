package io.konifer.domain.path

import io.konifer.infrastructure.properties.ConfigurationPropertyKeys.PathPropertyKeys.CacheControlPropertyKeys.ENABLED
import io.konifer.infrastructure.properties.ConfigurationPropertyKeys.PathPropertyKeys.CacheControlPropertyKeys.IMMUTABLE
import io.konifer.infrastructure.properties.ConfigurationPropertyKeys.PathPropertyKeys.CacheControlPropertyKeys.MAX_AGE
import io.konifer.infrastructure.properties.ConfigurationPropertyKeys.PathPropertyKeys.CacheControlPropertyKeys.REVALIDATE
import io.konifer.infrastructure.properties.ConfigurationPropertyKeys.PathPropertyKeys.CacheControlPropertyKeys.SHARED_MAX_AGE
import io.konifer.infrastructure.properties.ConfigurationPropertyKeys.PathPropertyKeys.CacheControlPropertyKeys.STALE_IF_ERROR
import io.konifer.infrastructure.properties.ConfigurationPropertyKeys.PathPropertyKeys.CacheControlPropertyKeys.STALE_WHILE_REVALIDATE
import io.konifer.infrastructure.properties.ConfigurationPropertyKeys.PathPropertyKeys.CacheControlPropertyKeys.VISIBILITY
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.tryGetString

enum class CacheControlVisibility(
    val value: String,
) {
    PUBLIC("public"),
    PRIVATE("private"),
    ;

    companion object Factory {
        fun fromConfig(value: String?): CacheControlVisibility = entries.first { it.value == value }
    }
}

enum class CacheControlRevalidate(
    val value: String,
) {
    MUST_REVALIDATE("must-revalidate"),
    PROXY_REVALIDATE("proxy-revalidate"),
    NO_CACHE("no-cache"),
    ;

    companion object Factory {
        fun fromConfig(value: String?): CacheControlRevalidate = CacheControlRevalidate.entries.first { it.value == value }
    }
}

data class CacheControlProperties(
    val enabled: Boolean,
    val maxAge: Long?,
    val sharedMaxAge: Long?,
    val visibility: CacheControlVisibility?,
    val revalidate: CacheControlRevalidate?,
    val staleWhileRevalidate: Long?,
    val staleIfError: Long?,
    val immutable: Boolean?,
) {
    init {
        maxAge?.let {
            require(it > 0) { "Max age must be positive" }
        }
        sharedMaxAge?.let {
            require(it > 0) { "Shared max age must be positive" }
        }
        staleWhileRevalidate?.let {
            require(it > 0) { "Stale while revalidate must be positive" }
        }
        staleIfError?.let {
            require(it > 0) { "Stale if error must be positive" }
        }
    }

    companion object Factory {
        val default =
            CacheControlProperties(
                enabled = false,
                maxAge = null,
                sharedMaxAge = null,
                visibility = null,
                revalidate = null,
                staleWhileRevalidate = null,
                staleIfError = null,
                immutable = null,
            )

        fun create(
            applicationConfig: ApplicationConfig?,
            parent: CacheControlProperties?,
        ): CacheControlProperties =
            CacheControlProperties(
                enabled = applicationConfig?.tryGetString(ENABLED)?.toBoolean() ?: parent?.enabled ?: false,
                maxAge = applicationConfig?.tryGetString(MAX_AGE)?.toLong() ?: parent?.maxAge,
                sharedMaxAge = applicationConfig?.tryGetString(SHARED_MAX_AGE)?.toLong() ?: parent?.sharedMaxAge,
                visibility =
                    applicationConfig
                        ?.tryGetString(VISIBILITY)
                        ?.let { CacheControlVisibility.fromConfig(it) }
                        ?: parent?.visibility,
                revalidate =
                    applicationConfig
                        ?.tryGetString(REVALIDATE)
                        ?.let { CacheControlRevalidate.fromConfig(it) }
                        ?: parent?.revalidate,
                staleWhileRevalidate =
                    applicationConfig
                        ?.tryGetString(
                            STALE_WHILE_REVALIDATE,
                        )?.toLong() ?: parent?.staleWhileRevalidate,
                staleIfError = applicationConfig?.tryGetString(STALE_IF_ERROR)?.toLong() ?: parent?.staleIfError,
                immutable = applicationConfig?.tryGetString(IMMUTABLE)?.toBoolean() ?: parent?.immutable,
            )
    }
}
