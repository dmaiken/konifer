package io.konifer.infrastructure.rules.inference.embedding

import io.konifer.infrastructure.rules.inference.EmbeddingModel
import konifer.jooq.tables.references.PROMPT_EMBEDDING
import kotlinx.coroutines.flow.associate
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.jooq.DSLContext
import java.time.LocalDateTime
import java.time.ZoneOffset.UTC
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

class PostgresEmbeddingCacheRepository(
    private val dslContext: DSLContext,
) : EmbeddingCacheRepository {
    override suspend fun fetchAll(model: EmbeddingModel): Map<String, FloatArray> =
        dslContext
            .select(PROMPT_EMBEDDING.PROMPT_TEXT, PROMPT_EMBEDDING.EMBEDDING)
            .from(PROMPT_EMBEDDING)
            .where(PROMPT_EMBEDDING.MODEL.eq(model.name))
            .asFlow()
            .associate { record ->
                val prompt =
                    requireNotNull(record.value1()) {
                        "Prompt embedding cache row contained a null prompt"
                    }
                val embedding =
                    requireNotNull(record.value2()) {
                        "Prompt embedding cache row for '$prompt' contained a null embedding"
                    }

                prompt to embedding.toFloatArray(prompt)
            }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun store(
        embeddingModel: EmbeddingModel,
        prompt: String,
        embeddings: FloatArray,
    ) {
        val embeddingArray: Array<Float?> = Array(embeddings.size) { index -> embeddings[index] }
        dslContext
            .insertInto(PROMPT_EMBEDDING)
            .set(PROMPT_EMBEDDING.ID, Uuid.generateV7().toJavaUuid())
            .set(PROMPT_EMBEDDING.MODEL, embeddingModel.name)
            .set(PROMPT_EMBEDDING.PROMPT_TEXT, prompt)
            .set(PROMPT_EMBEDDING.EMBEDDING, embeddingArray)
            .set(PROMPT_EMBEDDING.CREATED_AT, LocalDateTime.now(UTC))
            .onConflict(PROMPT_EMBEDDING.MODEL, PROMPT_EMBEDDING.PROMPT_TEXT)
            .doNothing()
            .awaitFirstOrNull()
    }

    private fun Array<Float?>.toFloatArray(prompt: String): FloatArray =
        FloatArray(size) { index ->
            requireNotNull(this[index]) {
                "Prompt embedding for '$prompt' contained null at index $index"
            }
        }
}
