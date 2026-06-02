package io.konifer.domain.image

import io.konifer.common.serializer.LowercaseEnumSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

@Serializable(with = LQIPImplementationSerializer::class)
enum class LQIPImplementation {
    BLURHASH,
    THUMBHASH,
}

class LQIPImplementationSerializer : KSerializer<LQIPImplementation> by LowercaseEnumSerializer(LQIPImplementation.entries)
