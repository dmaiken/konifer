package io.konifer.infrastructure.rules

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.LongBuffer
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt

class RulePromptEmbeddingService(
    private val tokenizer: Siglip2Tokenizer,
) {
    private val cache = ConcurrentHashMap<String, FloatArray>()

    var env = OrtEnvironment.getEnvironment()
    var session = env.createSession("text_model.onnx", OrtSession.SessionOptions())

    init {
        session.outputInfo.forEach { (name, info) ->
            println("Text model output: $name -> $info")
        }
    }

    fun generateEmbeddings(prompt: String) = cache.computeIfAbsent(prompt) { generate(prompt) }

    private fun generate(prompt: String): FloatArray {
        val encoded = tokenizer.encode(prompt)

        val inputIds = OnnxTensor.createTensor(
            env,
            LongBuffer.wrap(encoded.inputIds),
            longArrayOf(1, encoded.inputIds.size.toLong()),
        )
        val attentionMask = OnnxTensor.createTensor(
            env,
            LongBuffer.wrap(encoded.attentionMask),
            longArrayOf(1, encoded.attentionMask.size.toLong()),
        )

        inputIds.use {
            attentionMask.use {
                val outputs = session.run(
                    mapOf(
                        "input_ids" to inputIds,
                        "attention_mask" to attentionMask,
                    ),
                )

                outputs.use {
                    val embedding = extractPooledEmbedding(outputs)
                    return l2Normalize(embedding)
                }
            }
        }
    }

    private fun extractPooledEmbedding(outputs: OrtSession.Result): FloatArray {
        val output =
            outputs["text_embeds"]
                .orElseGet {
                    outputs["pooler_output"]
                        .orElseThrow {
                            IllegalStateException(
                                "Text model did not expose text_embeds or pooler_output. " +
                                        "Available outputs: ${outputs.map { it.key }}"
                            )
                        }
                }

        val tensor =
            output as? OnnxTensor
                ?: throw IllegalStateException("Expected pooled text embedding to be an OnnxTensor")

        return when (val value = tensor.value) {
            is Array<*> -> {
                val first = value.firstOrNull()
                    ?: throw IllegalStateException("Pooled text embedding output was empty")

                when (first) {
                    is FloatArray -> first
                    is Array<*> -> {
                        // Handles [1, 1, dim]-style outputs if an export has an extra dimension.
                        first.firstOrNull() as? FloatArray
                            ?: throw IllegalStateException("Unsupported nested text embedding shape")
                    }
                    else -> throw IllegalStateException(
                        "Unsupported pooled text embedding row type: ${first::class.qualifiedName}"
                    )
                }
            }

            is FloatArray -> value

            else -> throw IllegalStateException(
                "Unsupported pooled text embedding output type: ${value::class.qualifiedName}"
            )
        }
    }

    private fun l2Normalize(values: FloatArray): FloatArray {
        var sum = 0.0
        for (value in values) {
            sum += value * value
        }

        val norm = sqrt(sum).toFloat()
        require(norm > 0f) { "Cannot normalize zero-length embedding" }

        return FloatArray(values.size) { index -> values[index] / norm }
    }
}
