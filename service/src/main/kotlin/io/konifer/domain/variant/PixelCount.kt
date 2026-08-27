package io.konifer.domain.variant

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.math.BigDecimal

@JvmInline
@Serializable(with = PixelCountSerializer::class)
value class PixelCount(
    val value: Long,
) : Comparable<PixelCount> {
    init {
        require(value > 0) {
            "Pixel count must be positive: $value"
        }
    }

    override fun compareTo(other: PixelCount): Int = value.compareTo(other.value)

    companion object {
        private val PATTERN =
            Regex(
                """^(\d+(?:\.\d+)?)\s*(p|kp|mp|gp)?$""",
                RegexOption.IGNORE_CASE,
            )

        fun parse(input: String): PixelCount {
            val match =
                PATTERN.matchEntire(input.trim())
                    ?: throw IllegalArgumentException("Invalid pixel count: $input")

            val multiplier =
                when (match.groupValues[2].uppercase()) {
                    "", "P" -> BigDecimal.ONE
                    "KP" -> BigDecimal("1000")
                    "MP" -> BigDecimal("1000000")
                    "GP" -> BigDecimal("1000000000")
                    else -> error("Unreachable")
                }

            val pixels =
                try {
                    match.groupValues[1]
                        .toBigDecimal()
                        .multiply(multiplier)
                        .longValueExact()
                } catch (cause: ArithmeticException) {
                    throw IllegalArgumentException(
                        "Pixel count must resolve to a whole number within Long range: $input",
                        cause,
                    )
                }

            return PixelCount(pixels)
        }
    }
}

object PixelCountSerializer : KSerializer<PixelCount> {
    override val descriptor =
        PrimitiveSerialDescriptor("PixelCount", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): PixelCount = PixelCount.parse(decoder.decodeString())

    override fun serialize(
        encoder: Encoder,
        value: PixelCount,
    ) = encoder.encodeString("${value.value}P")
}

fun Long.toPixelCount(): PixelCount = PixelCount(this)
