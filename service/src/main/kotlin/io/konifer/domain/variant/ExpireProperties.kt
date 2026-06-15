package io.konifer.domain.variant

import io.konifer.infrastructure.property.ConfigurationPropertyKeys.PathPropertyKeys.TransformPropertyKeys.ExpirePropertyKeys
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Clock
import java.time.LocalDateTime
import kotlin.time.Duration
import kotlin.time.toJavaDuration

@Serializable
data class ExpireProperties(
    @SerialName(ExpirePropertyKeys.MODE)
    val mode: VariantExpirationMode = VariantExpirationMode.default,
    @SerialName(ExpirePropertyKeys.TTL)
    val ttl: Duration? = null,
) {
    companion object Factory {
        val default = ExpireProperties()
    }

    init {
        if (mode != VariantExpirationMode.NEVER) {
            require(ttl != null && ttl.inWholeMilliseconds > 0) {
                "For variant expiry mode: ${mode.name.lowercase()}, ${ExpirePropertyKeys.TTL} property must be set"
            }
        }
    }

    fun expiresAt(clock: Clock = Clock.systemUTC()): LocalDateTime? =
        if (mode ==
            VariantExpirationMode.NEVER
        ) {
            null
        } else {
            LocalDateTime.now(clock).plus(checkNotNull(ttl).toJavaDuration())
        }
}
