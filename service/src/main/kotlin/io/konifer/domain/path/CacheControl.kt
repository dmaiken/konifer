package io.konifer.domain.path

import io.konifer.common.serializer.LowercaseEnumSerializer
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.PathPropertyKeys.CacheControlPropertyKeys.ENABLED
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.PathPropertyKeys.CacheControlPropertyKeys.IMMUTABLE
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.PathPropertyKeys.CacheControlPropertyKeys.MAX_AGE
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.PathPropertyKeys.CacheControlPropertyKeys.REVALIDATE
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.PathPropertyKeys.CacheControlPropertyKeys.SHARED_MAX_AGE
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.PathPropertyKeys.CacheControlPropertyKeys.STALE_IF_ERROR
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.PathPropertyKeys.CacheControlPropertyKeys.STALE_WHILE_REVALIDATE
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.PathPropertyKeys.CacheControlPropertyKeys.VISIBILITY
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable(with = CacheControlVisibilitySerializer::class)
enum class CacheControlVisibility(
    val value: String,
) {
    PUBLIC("public"),
    PRIVATE("private"),
}

class CacheControlVisibilitySerializer : KSerializer<CacheControlVisibility> by LowercaseEnumSerializer(CacheControlVisibility.entries)

private object CacheControlRevalidationValues {
    const val MUST_REVALIDATE = "must-revalidate"
    const val PROXY_REVALIDATE = "proxy-revalidate"
    const val NO_CACHE = "no-cache"
}

@Serializable
enum class CacheControlRevalidate(
    val value: String,
) {
    @SerialName(CacheControlRevalidationValues.MUST_REVALIDATE)
    MUST_REVALIDATE(CacheControlRevalidationValues.MUST_REVALIDATE),

    @SerialName(CacheControlRevalidationValues.PROXY_REVALIDATE)
    PROXY_REVALIDATE(CacheControlRevalidationValues.PROXY_REVALIDATE),

    @SerialName(CacheControlRevalidationValues.NO_CACHE)
    NO_CACHE(CacheControlRevalidationValues.NO_CACHE),
}

@Serializable
data class CacheControlProperties(
    @SerialName(ENABLED)
    val enabled: Boolean = false,
    @SerialName(MAX_AGE)
    val maxAge: Long? = null,
    @SerialName(SHARED_MAX_AGE)
    val sharedMaxAge: Long? = null,
    @SerialName(VISIBILITY)
    val visibility: CacheControlVisibility? = null,
    @SerialName(REVALIDATE)
    val revalidate: CacheControlRevalidate? = null,
    @SerialName(STALE_WHILE_REVALIDATE)
    val staleWhileRevalidate: Long? = null,
    @SerialName(STALE_IF_ERROR)
    val staleIfError: Long? = null,
    @SerialName(IMMUTABLE)
    val immutable: Boolean? = null,
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
    }
}
