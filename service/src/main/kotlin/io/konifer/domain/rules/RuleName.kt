package io.konifer.domain.rules

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@JvmInline
@Serializable(with = RuleName.Serializer::class)
value class RuleName private constructor(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "Rule name cannot be blank" }
        require(value.length <= 32) { "Rule name cannot be longer than 32 characters" }
    }

    companion object {
        operator fun invoke(value: String): RuleName =
            RuleName(value.lowercase())
    }

    object Serializer : KSerializer<RuleName> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("RuleName", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): RuleName =
            invoke(decoder.decodeString())

        override fun serialize(encoder: Encoder, value: RuleName) {
            encoder.encodeString(value.value)
        }
    }
}
