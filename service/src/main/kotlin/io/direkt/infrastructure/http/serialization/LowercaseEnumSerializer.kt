package io.direkt.infrastructure.http.serialization

import io.direkt.domain.asset.AssetClass
import io.direkt.domain.asset.AssetSource
import io.direkt.domain.image.Filter
import io.direkt.domain.image.Fit
import io.direkt.domain.image.Flip
import io.direkt.domain.image.Gravity
import io.direkt.domain.image.ImageFormat
import io.direkt.domain.image.Rotate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.enums.EnumEntries

class LowercaseEnumSerializer<T : Enum<T>>(
    private val enumValues: EnumEntries<T>,
) : KSerializer<T> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LowercaseEnumSerializer", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: T,
    ) {
        encoder.encodeString(value.name.lowercase())
    }

    override fun deserialize(decoder: Decoder): T {
        val name = decoder.decodeString().uppercase()
        return enumValues.first { it.name == name }
    }
}

class AssetSourceSerializer : KSerializer<AssetSource> by LowercaseEnumSerializer(AssetSource.entries)

class AssetClassSerializer : KSerializer<AssetClass> by LowercaseEnumSerializer(AssetClass.entries)

class FitSerializer : KSerializer<Fit> by LowercaseEnumSerializer(Fit.entries)

class GravitySerializer : KSerializer<Gravity> by LowercaseEnumSerializer(Gravity.entries)

class ImageFormatSerializer : KSerializer<ImageFormat> by LowercaseEnumSerializer(ImageFormat.entries)

class RotateSerializer : KSerializer<Rotate> by LowercaseEnumSerializer(Rotate.entries)

class FlipSerializer : KSerializer<Flip> by LowercaseEnumSerializer(Flip.entries)

class FilterSerializer : KSerializer<Filter> by LowercaseEnumSerializer(Filter.entries)
