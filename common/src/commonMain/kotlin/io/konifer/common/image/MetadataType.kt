package io.konifer.common.image

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

private object MetadataTypeParameterValues {
    const val EXIF = "exif"
    const val XMP = "xmp"
    const val IPTC = "iptc"
    const val ICC = "icc"
}

@Serializable
enum class MetadataType {
    @SerialName(MetadataTypeParameterValues.EXIF)
    EXIF,

    @SerialName(MetadataTypeParameterValues.XMP)
    XMP,

    @SerialName(MetadataTypeParameterValues.IPTC)
    IPTC,

    @SerialName(MetadataTypeParameterValues.ICC)
    ICC,
}

object SortedMetadataTypeSerializer : KSerializer<List<MetadataType>> {
    // 1. Create a delegate that automatically uses your RotateSerializer
    private val delegate = ListSerializer(MetadataType.serializer())

    // 2. Inherit the exact same descriptor as a standard list
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(
        encoder: Encoder,
        value: List<MetadataType>,
    ) {
        val sortedList = value.sortedBy { it.name }

        delegate.serialize(encoder, sortedList)
    }

    override fun deserialize(decoder: Decoder): List<MetadataType> = delegate.deserialize(decoder)
}
