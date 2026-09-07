package io.konifer.domain.path

import io.konifer.common.serializer.LowercaseEnumSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

@Serializable(with = DeliveryStrategySerializer::class)
enum class DeliveryStrategy {
    SERVICE,
    PRESIGNED,
    TEMPLATE,
    ;

    companion object Factory {
        val default = SERVICE
    }
}

class DeliveryStrategySerializer : KSerializer<DeliveryStrategy> by LowercaseEnumSerializer(DeliveryStrategy.entries)
