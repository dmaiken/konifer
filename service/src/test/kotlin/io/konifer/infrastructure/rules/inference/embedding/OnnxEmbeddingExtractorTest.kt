package io.konifer.infrastructure.rules.inference.embedding

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OnnxValue
import ai.onnxruntime.OrtSession
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.util.Optional

class OnnxEmbeddingExtractorTest {
    @Test
    fun `extracts batched embeddings from primary output`() {
        val first = floatArrayOf(1f, 2f)
        val second = floatArrayOf(3f, 4f)
        val outputs = outputs(primary = Optional.of(tensor(arrayOf(first, second))))

        val embeddings =
            OnnxEmbeddingExtractor.extractPooledEmbeddings(
                outputs = outputs,
                primaryOutputName = PRIMARY_OUTPUT,
                modelDescription = MODEL_DESCRIPTION,
            )

        embeddings shouldContainExactly listOf(first, second)
    }

    @Test
    fun `falls back to pooler output when primary output is absent`() {
        val embedding = floatArrayOf(1f, 2f, 3f)
        val outputs = outputs(pooler = Optional.of(tensor(embedding)))

        val extracted =
            OnnxEmbeddingExtractor.extractPooledEmbedding(
                outputs = outputs,
                primaryOutputName = PRIMARY_OUTPUT,
                modelDescription = MODEL_DESCRIPTION,
            )

        extracted.contentEquals(embedding) shouldBe true
    }

    @Test
    fun `extracts first nested embedding from each row`() {
        val first = floatArrayOf(1f, 2f)
        val second = floatArrayOf(3f, 4f)
        val outputs = outputs(primary = Optional.of(tensor(arrayOf(arrayOf(first), arrayOf(second)))))

        val embeddings =
            OnnxEmbeddingExtractor.extractPooledEmbeddings(
                outputs = outputs,
                primaryOutputName = PRIMARY_OUTPUT,
                modelDescription = MODEL_DESCRIPTION,
            )

        embeddings shouldContainExactly listOf(first, second)
    }

    @Test
    fun `throws when single pooled embedding output is empty`() {
        val outputs = outputs(primary = Optional.of(tensor(emptyArray<FloatArray>())))

        val exception =
            shouldThrow<IllegalStateException> {
                OnnxEmbeddingExtractor.extractPooledEmbedding(
                    outputs = outputs,
                    primaryOutputName = PRIMARY_OUTPUT,
                    modelDescription = MODEL_DESCRIPTION,
                )
            }

        exception.message shouldBe "Pooled embedding output was empty"
    }

    @Test
    fun `throws with available outputs when neither expected output is exposed`() {
        val outputs = outputs(availableOutputNames = listOf("last_hidden_state", "logits"))

        val exception =
            shouldThrow<IllegalStateException> {
                OnnxEmbeddingExtractor.extractPooledEmbeddings(
                    outputs = outputs,
                    primaryOutputName = PRIMARY_OUTPUT,
                    modelDescription = MODEL_DESCRIPTION,
                )
            }

        exception.message shouldContain "$MODEL_DESCRIPTION did not expose $PRIMARY_OUTPUT or pooler_output"
        exception.message shouldContain "last_hidden_state"
        exception.message shouldContain "logits"
    }

    @Test
    fun `throws when selected output is not an OnnxTensor`() {
        val outputs = outputs(primary = Optional.of(mockk<OnnxValue>()))

        val exception =
            shouldThrow<IllegalStateException> {
                OnnxEmbeddingExtractor.extractPooledEmbeddings(
                    outputs = outputs,
                    primaryOutputName = PRIMARY_OUTPUT,
                    modelDescription = MODEL_DESCRIPTION,
                )
            }

        exception.message shouldBe "Expected pooled embedding output to be an OnnxTensor"
    }

    @Test
    fun `throws when tensor row has unsupported type`() {
        val outputs = outputs(primary = Optional.of(tensor(arrayOf("not an embedding"))))

        val exception =
            shouldThrow<IllegalStateException> {
                OnnxEmbeddingExtractor.extractPooledEmbeddings(
                    outputs = outputs,
                    primaryOutputName = PRIMARY_OUTPUT,
                    modelDescription = MODEL_DESCRIPTION,
                )
            }

        exception.message shouldBe "Unsupported pooled embedding row type: kotlin.String"
    }

    @Test
    fun `throws when nested tensor row does not contain an embedding`() {
        val outputs = outputs(primary = Optional.of(tensor(arrayOf(emptyArray<FloatArray>()))))

        val exception =
            shouldThrow<IllegalStateException> {
                OnnxEmbeddingExtractor.extractPooledEmbeddings(
                    outputs = outputs,
                    primaryOutputName = PRIMARY_OUTPUT,
                    modelDescription = MODEL_DESCRIPTION,
                )
            }

        exception.message shouldBe "Unsupported nested embedding shape"
    }

    @Test
    fun `throws when tensor value has unsupported type`() {
        val outputs = outputs(primary = Optional.of(tensor(intArrayOf(1, 2, 3))))

        val exception =
            shouldThrow<IllegalStateException> {
                OnnxEmbeddingExtractor.extractPooledEmbeddings(
                    outputs = outputs,
                    primaryOutputName = PRIMARY_OUTPUT,
                    modelDescription = MODEL_DESCRIPTION,
                )
            }

        exception.message shouldBe "Unsupported pooled embedding output type: kotlin.IntArray"
    }

    private fun tensor(value: Any): OnnxTensor =
        mockk {
            every { this@mockk.value } returns value
        }

    private fun outputs(
        primary: Optional<OnnxValue> = Optional.empty(),
        pooler: Optional<OnnxValue> = Optional.empty(),
        availableOutputNames: List<String> = emptyList(),
    ): OrtSession.Result {
        val result = mockk<OrtSession.Result>()
        every { result.get(PRIMARY_OUTPUT) } returns primary
        every { result.get("pooler_output") } returns pooler
        every { result.iterator() } answers {
            availableOutputNames
                .associateWith { mockk<OnnxValue>() }
                .toMutableMap()
                .entries
                .iterator()
        }
        return result
    }

    private companion object {
        const val PRIMARY_OUTPUT = "image_embeds"
        const val MODEL_DESCRIPTION = "Test model"
    }
}
