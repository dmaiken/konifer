package io.konifer.domain.variant

import io.konifer.infrastructure.property.ConfigurationPropertyKeys.PathPropertyKeys.TransformPropertyKeys.ExpirePropertyKeys
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.LocalDateTime
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

    fun expiresAt(clock: Clock = Clock.systemUTC()): LocalDateTime? =
        if (strategy ==
            VariantExpirationStrategy.NEVER
        ) {
            null
        } else {
            LocalDateTime.now(clock).plus(checkNotNull(ttl).toJavaDuration())
        }
}
