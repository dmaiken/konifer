package io.konifer.domain.transformation.pixel

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@JvmInline
@Serializable(with = PixelChannels.Serializer::class)
value class PixelChannels private constructor(
    val value: String,
) {
    init {
        require(value.length in (3..4)) {
            "Pixel channels must be between 3 and 4 characters"
        }
        require(value.all { char -> char in ALLOWED_CHANNELS }) {
            "Pixel channels can only contain ${ALLOWED_CHANNELS.joinToString()} (any case)"
        }
    }

    companion object {
        private val ALLOWED_CHANNELS = setOf('R', 'G', 'B', 'A')

        val default = "RGB".toPixelChannels()

        operator fun invoke(value: String): PixelChannels = PixelChannels(value.uppercase())
    }

    object Serializer : KSerializer<PixelChannels> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("PixelChannels", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): PixelChannels = invoke(decoder.decodeString())

        override fun serialize(
            encoder: Encoder,
            value: PixelChannels,
        ) {
            encoder.encodeString(value.value)
        }
    }
}

fun String.toPixelChannels() = PixelChannels(this)
