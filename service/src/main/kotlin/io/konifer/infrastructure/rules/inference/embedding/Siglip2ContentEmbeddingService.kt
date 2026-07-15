package io.konifer.infrastructure.rules.inference.embedding

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import io.konifer.infrastructure.rules.inference.EmbeddingModel
import io.konifer.infrastructure.rules.inference.OnnxSessionFactory
import io.konifer.infrastructure.rules.inference.embedding.OnnxEmbeddingExtractor.extractPooledEmbedding
import io.konifer.infrastructure.rules.l2Normalize
import io.konifer.infrastructure.variant.ImageTensor
import io.ktor.util.logging.KtorSimpleLogger
import java.nio.FloatBuffer

class Siglip2ContentEmbeddingService(
    private val ortEnvironment: OrtEnvironment,
    onnxSessionFactory: OnnxSessionFactory,
) : ContentEmbeddingService,
    AutoCloseable {
    companion object {
        private const val PIXEL_VALUES = "pixel_values"
        private const val IMAGE_EMBEDS = "image_embeds"
    }

    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)
    private val ortSession: OrtSession = onnxSessionFactory.create(EmbeddingModel.SIGLIP2_BASE_PATCH16_224_VISION)

    override fun generateEmbeddings(tensor: ImageTensor): FloatArray {
        OnnxTensor
            .createTensor(
                ortEnvironment,
                FloatBuffer.wrap(tensor.values),
                tensor.shape,
            ).use { tensor ->
                ortSession
                    .run(
                        mapOf(
                            PIXEL_VALUES to tensor,
                        ),
                    ).use { outputs ->
                        return extractPooledEmbedding(
                            outputs = outputs,
                            primaryOutputName = IMAGE_EMBEDS,
                            modelDescription = "Vision model",
                        ).l2Normalize()
                    }
            }
    }

    override fun close() {
        logger.info("Closing vision ORT session")
        ortSession.close()
    }
}
