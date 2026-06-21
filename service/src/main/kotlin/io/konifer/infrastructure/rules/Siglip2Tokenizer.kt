package io.konifer.infrastructure.rules

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer

/**
 * Tokenize using tokenizer.json from:
 * https://huggingface.co/google/siglip2-base-patch16-224
 */
class Siglip2Tokenizer {
    private val tokenizer =
        HuggingFaceTokenizer
            .builder()
            .optTokenizerName("google/siglip2-base-patch16-224")
            .optAddSpecialTokens(true)
            .optDoLowerCase(true)
            .optMaxLength(64)
            .optPadToMaxLength()
            .optTruncation(true)
            .build()

    fun encode(prompt: String): Siglip2Tokens =
        tokenizer.encode(prompt.trim()).let {
            Siglip2Tokens(
                inputIds = it.ids,
                attentionMask = it.attentionMask,
            )
        }
}

data class Siglip2Tokens(
    val inputIds: LongArray,
    val attentionMask: LongArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Siglip2Tokens

        if (!inputIds.contentEquals(other.inputIds)) return false
        if (!attentionMask.contentEquals(other.attentionMask)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = inputIds.contentHashCode()
        result = 31 * result + attentionMask.contentHashCode()
        return result
    }
}
