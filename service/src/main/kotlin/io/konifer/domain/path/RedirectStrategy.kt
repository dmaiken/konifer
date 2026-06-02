package io.konifer.domain.path

import io.konifer.common.serializer.LowercaseEnumSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

@Serializable(with = RedirectStrategySerializer::class)
enum class RedirectStrategy {
    NONE,
    PRESIGNED,
    TEMPLATE,
    ;

    companion object Factory {
        val default = NONE
    }
}

class RedirectStrategySerializer : KSerializer<RedirectStrategy> by LowercaseEnumSerializer(RedirectStrategy.entries)
