package io.konifer.infrastructure.datastore.postgres

import io.konifer.domain.asset.AssetPath
import io.konifer.domain.context.InvalidPathException
import org.jooq.postgres.extensions.types.Ltree

private const val TREE_ROOT = "root_v2"
private const val MAX_LABEL_LENGTH = 1000
private const val ESCAPED_PUNCTUATION = ".~!$'()*+=@:"
private const val HEX_DIGITS = "0123456789abcdef"

fun AssetPath.toLtree(): Ltree {
    if (this == AssetPath.ROOT) return Ltree.valueOf(TREE_ROOT)
    val labels =
        value.removePrefix("/").split('/').map { segment ->
            val encoded = segment.encodeLabel()
            if (encoded.length > MAX_LABEL_LENGTH) {
                throw InvalidPathException("Encoded asset path segment exceeds $MAX_LABEL_LENGTH characters")
            }
            encoded
        }
    return Ltree.valueOf((listOf(TREE_ROOT) + labels).joinToString("."))
}

fun Ltree.toAssetPath(): AssetPath {
    val path = data()
    require(path == TREE_ROOT || path.startsWith("$TREE_ROOT.")) { "Invalid asset tree path: $path" }
    if (path == TREE_ROOT) return AssetPath.ROOT
    val segments = path.removePrefix("$TREE_ROOT.").split('.').map { it.decodeLabel() }
    return AssetPath(segments.joinToString("/", prefix = "/"))
}

private fun String.encodeLabel(): String =
    buildString {
        for (character in this@encodeLabel) {
            when {
                character.isPlainLabelCharacter() -> {
                    append(character)
                }

                character == '_' -> {
                    append("__")
                }

                else -> {
                    append('_')
                    append(HEX_DIGITS[character.code shr 4])
                    append(HEX_DIGITS[character.code and 15])
                }
            }
        }
    }

private fun String.decodeLabel(): String {
    require(isNotEmpty() && length <= MAX_LABEL_LENGTH) { "Invalid encoded asset path label: $this" }
    return buildString {
        var index = 0
        while (index < this@decodeLabel.length) {
            val character = this@decodeLabel[index]
            when {
                character.isPlainLabelCharacter() -> {
                    append(character)
                    index++
                }

                this@decodeLabel.startsWith("__", index) -> {
                    append('_')
                    index += 2
                }

                character == '_' && index + 2 < this@decodeLabel.length -> {
                    val high = HEX_DIGITS.indexOf(this@decodeLabel[index + 1])
                    val low = HEX_DIGITS.indexOf(this@decodeLabel[index + 2])
                    require(high >= 0 && low >= 0) { "Invalid escape in asset path label: ${this@decodeLabel}" }
                    val decoded = (high * 16 + low).toChar()
                    require(decoded in ESCAPED_PUNCTUATION) {
                        "Noncanonical escape in asset path label: ${this@decodeLabel}"
                    }
                    append(decoded)
                    index += 3
                }

                else -> {
                    throw IllegalArgumentException("Invalid encoded asset path label: ${this@decodeLabel}")
                }
            }
        }
    }
}

private fun Char.isPlainLabelCharacter(): Boolean = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9' || this == '-'
