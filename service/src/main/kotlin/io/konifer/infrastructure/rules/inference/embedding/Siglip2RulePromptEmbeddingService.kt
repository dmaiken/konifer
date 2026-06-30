package io.konifer.infrastructure.rules.inference.embedding

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import io.konifer.domain.rules.RuleDefinition
import io.konifer.infrastructure.rules.inference.Siglip2Tokenizer
import io.konifer.infrastructure.rules.inference.embedding.OnnxEmbeddingExtractor.extractPooledEmbedding
import io.konifer.infrastructure.rules.l2Normalize
import java.nio.LongBuffer
import java.util.concurrent.ConcurrentHashMap

class Siglip2RulePromptEmbeddingService(
    tokenizer: Siglip2Tokenizer,
    ortEnvironment: OrtEnvironment,
    ortSession: OrtSession,
    ruleDefinitions: List<RuleDefinition>,
) : RulePromptEmbeddingService {
    companion object {
        private const val INPUT_IDS = "input_ids"
        private const val ATTENTION_MASK = "attention_mask"
        private const val TEXT_EMBEDS = "text_embeds"
    }

    private val rulePromptEmbeddings = ConcurrentHashMap<String, FloatArray>()

    init {
        ruleDefinitions.forEach { definition ->
            rulePromptEmbeddings[definition.prompt] =
                generate(
                    ortEnvironment = ortEnvironment,
                    ortSession = ortSession,
                    tokenizer = tokenizer,
                    prompt = definition.prompt,
                )
        }
    }

    override fun generateEmbeddings(prompt: String): FloatArray =
        rulePromptEmbeddings[prompt]
            ?: throw IllegalArgumentException("Embeddings for prompt: '$prompt' not found")

    private fun generate(
        ortEnvironment: OrtEnvironment,
        ortSession: OrtSession,
        tokenizer: Siglip2Tokenizer,
        prompt: String,
    ): FloatArray {
        val encoded = tokenizer.encode(prompt)

        val inputIds =
            OnnxTensor.createTensor(
                ortEnvironment,
                LongBuffer.wrap(encoded.inputIds),
                longArrayOf(1, encoded.inputIds.size.toLong()),
            )
        val attentionMask =
            OnnxTensor.createTensor(
                ortEnvironment,
                LongBuffer.wrap(encoded.attentionMask),
                longArrayOf(1, encoded.attentionMask.size.toLong()),
            )

        inputIds.use {
            attentionMask.use {
                val inputs =
                    buildMap {
                        put(INPUT_IDS, inputIds)
                        if (ortSession.inputNames.contains(ATTENTION_MASK)) {
                            put(ATTENTION_MASK, attentionMask)
                        }
                    }
                val outputs =
                    ortSession.run(inputs)

                outputs.use {
                    return extractPooledEmbedding(
                        outputs = outputs,
                        primaryOutputName = TEXT_EMBEDS,
                        modelDescription = "Text model",
                    ).l2Normalize()
                }
            }
        }
    }
}
