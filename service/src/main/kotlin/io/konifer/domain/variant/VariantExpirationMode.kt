package io.konifer.domain.variant

import io.konifer.common.serializer.LowercaseEnumSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

@Serializable(with = VariantExpirationSerializer::class)
enum class VariantExpirationMode {
    NEVER,
    TTL,
    ;

    companion object Factory {
        val default = NEVER
    }
}

class VariantExpirationSerializer : KSerializer<VariantExpirationMode> by LowercaseEnumSerializer(VariantExpirationMode.entries)
