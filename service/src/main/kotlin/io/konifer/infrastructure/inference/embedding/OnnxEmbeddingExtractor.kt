package io.konifer.infrastructure.inference.embedding

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtSession

object OnnxEmbeddingExtractor {
    private const val POOLER_OUTPUT = "pooler_output"

    fun extractPooledEmbedding(
        outputs: OrtSession.Result,
        primaryOutputName: String,
        modelDescription: String,
    ): FloatArray {
        val output =
            outputs[primaryOutputName]
                .orElseGet {
                    outputs[POOLER_OUTPUT]
                        .orElseThrow {
                            IllegalStateException(
                                "$modelDescription did not expose $primaryOutputName or $POOLER_OUTPUT. " +
                                    "Available outputs: ${outputs.map { it.key }}",
                            )
                        }
                }

        val tensor =
            output as? OnnxTensor
                ?: throw IllegalStateException("Expected pooled embedding output to be an OnnxTensor")

        return when (val value = tensor.value) {
            is Array<*> -> {
                val first =
                    value.firstOrNull()
                        ?: throw IllegalStateException("Pooled embedding output was empty")

                when (first) {
                    is FloatArray -> first
                    is Array<*> ->
                        first.firstOrNull() as? FloatArray
                            ?: throw IllegalStateException("Unsupported nested embedding shape")
                    else -> throw IllegalStateException(
                        "Unsupported pooled embedding row type: ${first::class.qualifiedName}",
                    )
                }
            }
            is FloatArray -> value
            else -> throw IllegalStateException(
                "Unsupported pooled embedding output type: ${value::class.qualifiedName}",
            )
        }
    }
}
