package io.konifer.infrastructure.rules.inference

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer

/**
 * Tokenize using tokenizer.json from the configured SigLIP2 model directory.
 */
class Siglip2Tokenizer : AutoCloseable {
    private val tokenizer =
        HuggingFaceTokenizer
            .builder()
            .optTokenizerPath(Siglip2ModelFiles.tokenizer())
            .optAddSpecialTokens(true)
            .optDoLowerCase(true)
            .optMaxLength(64)
            .optPadToMaxLength()
            .optTruncation(true)
            .build()

    fun encodeBatch(prompts: List<String>): List<Siglip2Tokens> =
        tokenizer
            .batchEncode(prompts.map { it.trim() }.toTypedArray())
            .map {
                Siglip2Tokens(
                    inputIds = it.ids,
                    attentionMask = it.attentionMask,
                )
            }

    override fun close() {
        tokenizer.close()
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
