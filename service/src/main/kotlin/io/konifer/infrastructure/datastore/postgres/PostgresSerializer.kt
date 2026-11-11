package io.konifer.infrastructure.datastore.postgres

import io.konifer.domain.image.ColorSpace
import io.konifer.domain.image.toColorSpace
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
        ignoreUnknownKeys = false
        encodeDefaults = false
        explicitNulls = false
    }

/**
 * Reads attributes and LQIPs written by newer releases while preserving known metadata.
 * Additive properties must not change the interpretation of existing fields.
 * Transformation identity still requires the strict [postgresJson] reader.
 */
val postgresMetadataJson: Json =
    Json(postgresJson) {
        ignoreUnknownKeys = true
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

    override fun deserialize(decoder: Decoder): ColorSpace = decoder.decodeString().toColorSpace()
}
