package io.konifer.domain.variant.retention

import io.konifer.infrastructure.property.ConfigurationPropertyKeys.PathPropertyKeys.TransformPropertyKeys.RetentionPropertyKeys
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RetentionProperties(
    @SerialName(RetentionPropertyKeys.EXPIRE)
    val expire: ExpirationProperties = ExpirationProperties(),
    @SerialName(RetentionPropertyKeys.CACHE)
    val cache: CacheProperties = CacheProperties(),
) {
    companion object {
        val default = RetentionProperties()
    }
}
