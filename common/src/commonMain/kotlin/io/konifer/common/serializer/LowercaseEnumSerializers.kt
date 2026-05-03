package io.konifer.common.serializer

import io.konifer.common.asset.AssetClass
import io.konifer.common.asset.AssetSource
import io.konifer.common.image.Filter
import io.konifer.common.image.Fit
import io.konifer.common.image.Flip
import io.konifer.common.image.Gravity
import io.konifer.common.image.MetadataType
import io.konifer.common.image.Rotate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
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

class SortedLowercaseEnumSetSerializer<T : Enum<T>>(
    enumValues: EnumEntries<T>,
) : KSerializer<List<T>> {
    private val delegateSerializer = ListSerializer(LowercaseEnumSerializer(enumValues))

    override val descriptor: SerialDescriptor = delegateSerializer.descriptor

    override fun serialize(
        encoder: Encoder,
        value: List<T>,
    ) {
        val sortedList = value.sortedBy { it.name }

        delegateSerializer.serialize(encoder, sortedList)
    }

    override fun deserialize(decoder: Decoder): List<T> = delegateSerializer.deserialize(decoder)
}

class AssetSourceSerializer : KSerializer<AssetSource> by LowercaseEnumSerializer(AssetSource.entries)

class AssetClassSerializer : KSerializer<AssetClass> by LowercaseEnumSerializer(AssetClass.entries)

class FitSerializer : KSerializer<Fit> by LowercaseEnumSerializer(Fit.entries)

class GravitySerializer : KSerializer<Gravity> by LowercaseEnumSerializer(Gravity.entries)

class RotateSerializer : KSerializer<Rotate> by LowercaseEnumSerializer(Rotate.entries)

class FlipSerializer : KSerializer<Flip> by LowercaseEnumSerializer(Flip.entries)

class FilterSerializer : KSerializer<Filter> by LowercaseEnumSerializer(Filter.entries)

class MetadataCollectionTypeSerializer : KSerializer<List<MetadataType>> by SortedLowercaseEnumSetSerializer(MetadataType.entries)
