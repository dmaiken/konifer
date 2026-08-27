package io.konifer.domain

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@JvmInline
@Serializable(with = ByteSizeSerializer::class)
value class ByteSize(
    val bytes: Long,
) : Comparable<ByteSize> {
    init {
        require(bytes > 0) { "Byte size must be positive: $bytes" }
    }

    override fun compareTo(other: ByteSize): Int = bytes.compareTo(other.bytes)

    companion object {
        fun parse(value: String): ByteSize {
            val match =
                PATTERN.matchEntire(value.trim())
                    ?: throw IllegalArgumentException("Invalid byte size: $value")

            val amount = match.groupValues[1].toLong()
            val multiplier =
                when (match.groupValues[2].uppercase()) {
                    "", "B" -> 1L
                    "KB" -> 1_000L
                    "MB" -> 1_000_000L
                    "GB" -> 1_000_000_000L
                    "KIB" -> 1L shl 10
                    "MIB" -> 1L shl 20
                    "GIB" -> 1L shl 30
                    else -> error("Unreachable")
                }

            return ByteSize(Math.multiplyExact(amount, multiplier))
        }

        private val PATTERN =
            Regex("""^(\d+)\s*(b|kb|mb|gb|kib|mib|gib)?$""", RegexOption.IGNORE_CASE)
    }
}

object ByteSizeSerializer : KSerializer<ByteSize> {
    override val descriptor =
        PrimitiveSerialDescriptor("ByteSize", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): ByteSize = ByteSize.parse(decoder.decodeString())

    override fun serialize(
        encoder: Encoder,
        value: ByteSize,
    ) = encoder.encodeString("${value.bytes}B")
}
