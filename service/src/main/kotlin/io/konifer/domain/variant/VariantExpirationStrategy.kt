package io.konifer.domain.variant

import io.konifer.common.serializer.LowercaseEnumSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

@Serializable(with = VariantExpirationStrategySerializer::class)
enum class VariantExpirationStrategy {
    NEVER,
    TTL,
    IDLE,
    ;

    companion object Factory {
        val default = NEVER
    }
}

class VariantExpirationStrategySerializer :
    KSerializer<VariantExpirationStrategy> by LowercaseEnumSerializer(VariantExpirationStrategy.entries)
