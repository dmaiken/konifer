package io.konifer.infrastructure.rules.inference.embedding

import io.konifer.infrastructure.vips.processor.ImageTensor

interface ContentEmbeddingService {
    fun generateEmbeddings(tensor: ImageTensor): FloatArray
}
