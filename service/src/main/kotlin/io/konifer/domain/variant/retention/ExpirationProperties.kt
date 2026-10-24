package io.konifer.domain.variant.retention

import io.konifer.infrastructure.property.ConfigurationPropertyKeys.PathPropertyKeys.TransformPropertyKeys.ExpirePropertyKeys
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.toJavaDuration

@Serializable
data class ExpirationProperties(
    @SerialName(ExpirePropertyKeys.STRATEGY)
    val strategy: VariantExpirationStrategy = VariantExpirationStrategy.default,
    @SerialName(ExpirePropertyKeys.TTL)
    val ttl: Duration? = null,
) {
    companion object Factory {
        val default = ExpirationProperties()
    }

    init {
        if (strategy != VariantExpirationStrategy.NEVER) {
            require(ttl != null && ttl.inWholeMilliseconds > 0) {
                "For variant expiry strategy: '${Json.encodeToString(strategy)}', '${ExpirePropertyKeys.TTL}' property must be set"
            }
        }
    }

    fun expiresAt(clock: Clock = Clock.systemUTC()): Instant? =
        if (strategy ==
            VariantExpirationStrategy.NEVER
        ) {
            null
        } else {
            Instant.now(clock).plus(checkNotNull(ttl).toJavaDuration())
        }
}
