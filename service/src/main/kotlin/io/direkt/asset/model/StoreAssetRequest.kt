package io.direkt.asset.model

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

private const val MAX_TAG_VALUE_LENGTH: Int = 256

@Serializable
data class StoreAssetRequest(
    val alt: String? = null,
    val url: String? = null,
    val labels: Map<String, String> = emptyMap(),
    val tags: Set<String> = emptySet(),
) {
    fun validate() {
        if (alt != null && alt.length > MAX_ALT_LENGTH) {
            throw IllegalArgumentException("Alt exceeds max length of $MAX_ALT_LENGTH")
        }
        if (labels.any { it.key.length > MAX_LABEL_KEY_LENGTH || it.value.length > MAX_LABEL_VALUE_LENGTH }) {
            throw IllegalArgumentException("Labels exceed max length of ($MAX_LABEL_KEY_LENGTH, $MAX_LABEL_VALUE_LENGTH)")
        }
        if (labels.size > MAX_LABELS) {
            throw IllegalArgumentException("Cannot have more than $MAX_LABELS labels")
        }
        if (tags.any { it.length > MAX_TAG_VALUE_LENGTH }) {
            throw IllegalArgumentException("Tags exceed max length of $MAX_TAG_VALUE_LENGTH")
        }
    }
}
