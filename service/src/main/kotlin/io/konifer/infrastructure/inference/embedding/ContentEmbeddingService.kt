package io.konifer.infrastructure.inference.embedding

import io.konifer.infrastructure.variant.ImageTensor

interface ContentEmbeddingService {
    fun generateEmbeddings(tensor: ImageTensor): FloatArray
}
