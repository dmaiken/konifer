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
        val channels = value.toSet()
        require(
            (value.length == 3 && channels == RGB_CHANNELS) ||
                (value.length == 4 && channels == RGBA_CHANNELS),
        ) {
            "Pixel channels must contain R, G, B exactly once and optionally A once (any case)"
        }
    }

    companion object {
        private val RGB_CHANNELS = setOf('R', 'G', 'B')
        private val RGBA_CHANNELS = setOf('R', 'G', 'B', 'A')

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
