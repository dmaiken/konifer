package io.konifer.domain.asset

private const val MAX_TAG_VALUE_LENGTH: Int = 256

/**
 * Inspired from AWS limit
 */
const val MAX_TAGS: Int = 50

@JvmInline
value class AssetTag(
    val value: String,
) {
    init {
        if (value.isBlank()) {
            throw InvalidAssetException("Asset tag cannot be blank")
        }
        if (value.length > MAX_TAG_VALUE_LENGTH) {
            throw InvalidAssetException("Tags exceeds max length of $MAX_TAG_VALUE_LENGTH characters")
        }
    }
}

class AssetTags private constructor(
    private val values: Set<AssetTag>,
) {
    init {
        if (values.size > MAX_TAGS) {
            throw InvalidAssetException("Cannot have more than $MAX_TAGS tags")
        }
    }

    fun asSet(): Set<String> = values.map { it.value }.toSet()

    fun isEmpty(): Boolean = values.isEmpty()

    companion object Factory {
        val EMPTY = AssetTags(emptySet())

        fun from(tags: Set<String>): AssetTags = AssetTags(tags.map { AssetTag(it) }.toSet())
    }
}

fun Set<String>.toAssetTags() = AssetTags.from(this)
