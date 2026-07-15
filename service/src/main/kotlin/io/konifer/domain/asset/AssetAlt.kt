package io.konifer.domain.asset

/**
 * Looks like some assistive devices truncate alts after 125 characters
 */
private const val MAX_ALT_LENGTH: Int = 125

@JvmInline
value class AssetAlt(val value: String) {
    init {
        require(value.length <= MAX_ALT_LENGTH) { "Asset alt cannot exceed $MAX_ALT_LENGTH characters" }
    }
}

fun String.toAssetAlt() = AssetAlt(this)
