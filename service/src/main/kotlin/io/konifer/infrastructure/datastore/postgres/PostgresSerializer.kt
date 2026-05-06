package io.konifer.infrastructure.datastore.postgres

import io.konifer.common.image.ColorSpace
import io.konifer.common.image.ColorSpaceNames
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json

/**
 * Serializer that is configured to not serialize null values.
 *
 * These settings are very important! We do not want to serialize null fields or default values.
 */
val postgresJson: Json =
    Json {
        encodeDefaults = false
        explicitNulls = false
    }

class ColorSpaceSerializer : KSerializer<ColorSpace> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LowercaseEnumSerializer", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: ColorSpace,
    ) {
        encoder.encodeString(value.name.lowercase())
    }

    override fun deserialize(decoder: Decoder): ColorSpace {
        val name = decoder.decodeString().lowercase()
        return when (name.lowercase()) {
            ColorSpaceNames.SRGB -> ColorSpace.SRGB
            ColorSpaceNames.P3 -> ColorSpace.P3
            ColorSpaceNames.ADOBE_RGB -> ColorSpace.AdobeRGB
            ColorSpaceNames.CYMK -> ColorSpace.CMYK
            ColorSpaceNames.GRAYSCALE -> ColorSpace.Grayscale
            ColorSpaceNames.UNKNOWN -> ColorSpace.Unknown
            // If it doesn't match our known enums, wrap it in the Custom class
            else -> ColorSpace.Custom(name)
        }
    }
}
