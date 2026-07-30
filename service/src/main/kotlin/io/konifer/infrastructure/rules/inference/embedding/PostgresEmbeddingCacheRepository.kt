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
    override suspend fun fetch(
        prompts: List<String>,
        embeddingModel: EmbeddingModel,
    ): Map<String, FloatArray> {
        if (prompts.isEmpty()) return emptyMap()

        return dslContext
            .select(PROMPT_EMBEDDING.PROMPT_TEXT, PROMPT_EMBEDDING.EMBEDDING)
            .from(PROMPT_EMBEDDING)
            .where(PROMPT_EMBEDDING.MODEL.eq(embeddingModel.name))
            .and(PROMPT_EMBEDDING.PROMPT_TEXT.`in`(prompts))
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
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun storeAll(
        embeddingModel: EmbeddingModel,
        prompts: Map<String, FloatArray>,
    ) {
        if (prompts.isEmpty()) return

        val insert =
            dslContext.insertInto(
                PROMPT_EMBEDDING,
                PROMPT_EMBEDDING.ID,
                PROMPT_EMBEDDING.MODEL,
                PROMPT_EMBEDDING.PROMPT_TEXT,
                PROMPT_EMBEDDING.EMBEDDING,
                PROMPT_EMBEDDING.CREATED_AT,
            )
        prompts.forEach { (prompt, embeddings) ->
            insert.values(
                Uuid.generateV7().toJavaUuid(),
                embeddingModel.name,
                prompt,
                embeddings.toPostgresArray(),
                LocalDateTime.now(UTC),
            )
        }

        insert
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

    private fun FloatArray.toPostgresArray(): Array<Float?> = Array(size) { index -> this[index] }
}
