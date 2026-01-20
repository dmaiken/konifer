package io.konifer.infrastructure

import kotlinx.serialization.Serializable

/**
 * Looks like some assistive devices truncate alts after 125 characters
 */
private const val MAX_ALT_LENGTH: Int = 125

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

private const val MAX_TAGS: Int = 50

private const val MAX_TAG_VALUE_LENGTH: Int = 256

@Serializable
data class StoreAssetRequest(
    val alt: String? = null,
    val url: String? = null,
    val labels: Map<String, String> = emptyMap(),
    val tags: Set<String> = emptySet(),
) {
    init {
        if (alt != null) {
            require(alt.length <= MAX_ALT_LENGTH) { "Alt exceeds max length of $MAX_ALT_LENGTH" }
        }
        require(labels.all { it.key.length <= MAX_LABEL_KEY_LENGTH && it.value.length <= MAX_LABEL_VALUE_LENGTH }) {
            "Labels exceed max length of ($MAX_LABEL_KEY_LENGTH, $MAX_LABEL_VALUE_LENGTH)"
        }
        require(labels.size <= MAX_LABELS) { "Cannot have more than $MAX_LABELS labels" }

        require(tags.all { it.length <= MAX_TAG_VALUE_LENGTH }) { "Tags exceeds max length of $MAX_TAG_VALUE_LENGTH" }
        require(tags.size <= MAX_TAGS) { "Tags exceeds max length of $MAX_TAGS" }
    }
}
