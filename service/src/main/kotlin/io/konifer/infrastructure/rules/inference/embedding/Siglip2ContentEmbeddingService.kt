package io.konifer.infrastructure.rules.inference.embedding

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import io.konifer.infrastructure.rules.inference.embedding.OnnxEmbeddingExtractor.extractPooledEmbedding
import io.konifer.infrastructure.rules.l2Normalize
import io.konifer.infrastructure.variant.ImageTensor
import java.nio.FloatBuffer

class Siglip2ContentEmbeddingService(
    private val ortEnvironment: OrtEnvironment,
    private val ortSession: OrtSession,
) : ContentEmbeddingService {
    companion object {
        private const val PIXEL_VALUES = "pixel_values"
        private const val IMAGE_EMBEDS = "image_embeds"
    }

    override fun generateEmbeddings(tensor: ImageTensor): FloatArray {
        val input =
            OnnxTensor.createTensor(
                ortEnvironment,
                FloatBuffer.wrap(tensor.values),
                tensor.shape,
            )

        input.use {
            val outputs =
                ortSession.run(
                    mapOf(
                        PIXEL_VALUES to input,
                    ),
                )

            outputs.use {
                return extractPooledEmbedding(
                    outputs = outputs,
                    primaryOutputName = IMAGE_EMBEDS,
                    modelDescription = "Vision model",
                ).l2Normalize()
            }
        }
    }
}
