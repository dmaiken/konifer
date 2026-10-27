package io.konifer.domain.asset

import io.konifer.domain.context.InvalidLabelsException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Inspired from AWS limit
 */
private const val MAX_LABEL_KEY_LENGTH: Int = 128

/**
 * Inspired from AWS limit
 */
private const val MAX_LABEL_VALUE_LENGTH: Int = 256

/**
 * Inspired from AWS limit
 */
private const val MAX_LABELS: Int = 50

@JvmInline
value class LabelKey(
    val key: String,
) {
    init {
        if (key.isBlank()) {
            throw InvalidLabelsException("Label key cannot be blank")
        }
        if (key.length > MAX_LABEL_KEY_LENGTH) {
            throw InvalidLabelsException("Label key cannot exceed $MAX_LABEL_KEY_LENGTH characters")
        }
    }
}

@JvmInline
value class LabelValue(
    val value: String,
) {
    init {
        if (value.isBlank()) {
            throw InvalidLabelsException("Label value cannot be blank")
        }
        if (value.length > MAX_LABEL_VALUE_LENGTH) {
            throw InvalidLabelsException("Label value cannot exceed $MAX_LABEL_VALUE_LENGTH characters")
        }
    }
}

@Serializable(with = AssetLabels.Serializer::class)
class AssetLabels private constructor(
    private val values: Map<LabelKey, LabelValue>,
) {
    init {
        if (values.size > MAX_LABELS) {
            throw InvalidLabelsException("Cannot have more than $MAX_LABELS labels")
        }
    }

    companion object Factory {
        val empty = AssetLabels(emptyMap())

        fun from(labels: Map<String, String>): AssetLabels =
            AssetLabels(
                labels
                    .mapKeys { LabelKey(it.key) }
                    .mapValues { LabelValue(it.value) },
            )
    }

    object Serializer : KSerializer<AssetLabels> {
        private val delegate =
            MapSerializer(String.serializer(), String.serializer())

        override val descriptor: SerialDescriptor =
            delegate.descriptor

        override fun deserialize(decoder: Decoder): AssetLabels = from(delegate.deserialize(decoder))

        override fun serialize(
            encoder: Encoder,
            value: AssetLabels,
        ) {
            delegate.serialize(encoder, value.asMap())
        }
    }

    fun asMap(): Map<String, String> =
        values
            .mapKeys { it.key.key }
            .mapValues { it.value.value }

    fun isEmpty(): Boolean = values.isEmpty()

    fun merge(other: AssetLabels): AssetLabels = AssetLabels(values + other.values)
}

fun Map<String, String>.toAssetLabels() = AssetLabels.from(this)

fun Collection<AssetLabels>.merge(): AssetLabels =
    fold(emptyMap<String, String>()) { merged, labels ->
        merged + labels.asMap()
    }.toAssetLabels()
