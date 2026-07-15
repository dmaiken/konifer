package io.konifer.infrastructure.rules.inference.embedding

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtSession

object OnnxEmbeddingExtractor {
    private const val POOLER_OUTPUT = "pooler_output"

    fun extractPooledEmbedding(
        outputs: OrtSession.Result,
        primaryOutputName: String,
        modelDescription: String,
    ): FloatArray =
        extractPooledEmbeddings(
            outputs = outputs,
            primaryOutputName = primaryOutputName,
            modelDescription = modelDescription,
        ).firstOrNull()
            ?: throw IllegalStateException("Pooled embedding output was empty")

    fun extractPooledEmbeddings(
        outputs: OrtSession.Result,
        primaryOutputName: String,
        modelDescription: String,
    ): List<FloatArray> {
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
            is Array<*> ->
                value.map { row ->
                    when (row) {
                        is FloatArray -> row
                        is Array<*> ->
                            row.firstOrNull() as? FloatArray
                                ?: throw IllegalStateException("Unsupported nested embedding shape")
                        else -> throw IllegalStateException(
                            "Unsupported pooled embedding row type: ${row?.let { it::class.qualifiedName }}",
                        )
                    }
                }
            is FloatArray -> listOf(value)
            else -> throw IllegalStateException(
                "Unsupported pooled embedding output type: ${value::class.qualifiedName}",
            )
        }
    }
}
