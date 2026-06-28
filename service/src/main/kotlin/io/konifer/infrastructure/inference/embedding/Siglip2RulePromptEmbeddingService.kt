package io.konifer.infrastructure.inference.embedding

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import io.konifer.domain.rules.RuleDefinition
import io.konifer.infrastructure.inference.Siglip2Tokenizer
import io.konifer.infrastructure.inference.embedding.OnnxEmbeddingExtractor.extractPooledEmbedding
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
                val outputs =
                    ortSession.run(
                        mapOf(
                            INPUT_IDS to inputIds,
                        ),
                    )

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
