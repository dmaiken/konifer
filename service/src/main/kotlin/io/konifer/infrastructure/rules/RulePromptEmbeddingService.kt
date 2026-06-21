package io.konifer.infrastructure.rules

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.LongBuffer
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.pathString

class RulePromptEmbeddingService(
    private val tokenizer: Siglip2Tokenizer,
    private val ortEnvironment: OrtEnvironment,
    pathToModel: Path,
) {
    companion object {
        private const val INPUT_IDS = "input_ids"
        private const val TEXT_EMBEDS = "text_embeds"
        private const val POOLER_OUTPUT = "pooler_output"
    }

    private val cache = ConcurrentHashMap<String, FloatArray>()
    private val session = ortEnvironment.createSession(pathToModel.pathString, OrtSession.SessionOptions())

    init {
        session.inputInfo.forEach { (name, info) ->
            println("Text model input: $name -> $info")
        }
        session.outputInfo.forEach { (name, info) ->
            println("Text model output: $name -> $info")
        }
    }

    fun generateEmbeddings(prompt: String): FloatArray = cache.computeIfAbsent(prompt) { generate(prompt) }

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
                    session.run(
                        mapOf(
                            INPUT_IDS to inputIds,
                        ),
                    )

                outputs.use {
                    val embedding = extractPooledEmbedding(outputs)
                    return embedding.l2Normalize()
                }
            }
        }
    }

    private fun extractPooledEmbedding(outputs: OrtSession.Result): FloatArray {
        val output =
            outputs[TEXT_EMBEDS]
                .orElseGet {
                    outputs[POOLER_OUTPUT]
                        .orElseThrow {
                            IllegalStateException(
                                "Text model did not expose text_embeds or pooler_output. " +
                                    "Available outputs: ${outputs.map { it.key }}",
                            )
                        }
                }

        val tensor =
            output as? OnnxTensor
                ?: throw IllegalStateException("Expected pooled text embedding to be an OnnxTensor")

        return when (val value = tensor.value) {
            is Array<*> -> {
                val first =
                    value.firstOrNull()
                        ?: throw IllegalStateException("Pooled text embedding output was empty")

                when (first) {
                    is FloatArray -> first
                    is Array<*> -> {
                        // Handles [1, 1, dim]-style outputs if an export has an extra dimension.
                        first.firstOrNull() as? FloatArray
                            ?: throw IllegalStateException("Unsupported nested text embedding shape")
                    }
                    else -> throw IllegalStateException(
                        "Unsupported pooled text embedding row type: ${first::class.qualifiedName}",
                    )
                }
            }

            is FloatArray -> value

            else -> throw IllegalStateException(
                "Unsupported pooled text embedding output type: ${value::class.qualifiedName}",
            )
        }
    }
}
