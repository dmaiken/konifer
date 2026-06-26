package io.konifer.infrastructure.inference.embedding

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import io.konifer.infrastructure.inference.Siglip2Tokenizer
import io.konifer.infrastructure.inference.embedding.OnnxEmbeddingExtractor.extractPooledEmbedding
import io.konifer.infrastructure.rules.l2Normalize
import java.nio.LongBuffer
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.pathString

class Siglip2RulePromptEmbeddingService(
    private val tokenizer: Siglip2Tokenizer,
    private val ortEnvironment: OrtEnvironment,
    private val ortSession: OrtSession,
): RulePromptEmbeddingService {
    companion object {
        private const val INPUT_IDS = "input_ids"
        private const val TEXT_EMBEDS = "text_embeds"
    }

    private val cache = ConcurrentHashMap<String, FloatArray>()

    init {
        ortSession.inputInfo.forEach { (name, info) ->
            println("Text model input: $name -> $info")
        }
        ortSession.outputInfo.forEach { (name, info) ->
            println("Text model output: $name -> $info")
        }
    }

    override fun generateEmbeddings(prompt: String): FloatArray = cache.computeIfAbsent(prompt) { generate(prompt) }

    private fun generate(prompt: String): FloatArray {
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
