package io.konifer.infrastructure.rules.inference.embedding

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import io.konifer.domain.rules.RuleDefinition
import io.konifer.infrastructure.rules.inference.EmbeddingModel
import io.konifer.infrastructure.rules.inference.OnnxSessionFactory
import io.konifer.infrastructure.rules.inference.Siglip2Tokenizer
import io.konifer.infrastructure.rules.inference.embedding.OnnxEmbeddingExtractor.extractPooledEmbeddings
import io.konifer.infrastructure.rules.l2Normalize
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.nio.LongBuffer
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.measureTimedValue

class Siglip2RulePromptEmbeddingService(
    ortEnvironment: OrtEnvironment,
    onnxSessionFactory: OnnxSessionFactory,
    ruleDefinitions: List<RuleDefinition>,
    private val embeddingCacheRepository: EmbeddingCacheRepository,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val tokenizerFactory: () -> Siglip2Tokenizer = ::Siglip2Tokenizer,
) : RulePromptEmbeddingService,
    AutoCloseable {
    companion object {
        private const val INPUT_IDS = "input_ids"
        private const val ATTENTION_MASK = "attention_mask"
        private const val TEXT_EMBEDS = "text_embeds"
        private const val PROMPT_PREFIX = "this is a photo of "
        private const val EMBEDDING_BATCH_SIZE = 32
    }

    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val rulePromptEmbeddings = ConcurrentHashMap<String, FloatArray>()
    private val warmupJob =
        scope.async {
            val prompts =
                ruleDefinitions
                    .flatMap { it.prompts }
                    .map(::preprocessPrompt)
                    .distinct()

            if (prompts.isEmpty()) return@async

            // fetch cached embeddings and see if we even need the session/tokenizer
            val cached =
                embeddingCacheRepository.fetchAll(EmbeddingModel.SIGLIP2_BASE_PATCH16_224_TEXT).also {
                    rulePromptEmbeddings.putAll(it)
                }
            val promptsRequiringGeneration = prompts.filterNot { cached.containsKey(it) }
            if (promptsRequiringGeneration.isNotEmpty()) {
                generateAndCacheEmbeddings(
                    ortEnvironment = ortEnvironment,
                    onnxSessionFactory = onnxSessionFactory,
                    prompts = promptsRequiringGeneration,
                )
            } else {
                logger.info("All prompts found in embedding cache, skipping siglip2 text model initialization")
            }
        }

    override fun generateEmbeddings(prompt: String): FloatArray {
        val preprocessed = preprocessPrompt(prompt)

        rulePromptEmbeddings[preprocessed]?.let { return it }

        try {
            runBlocking {
                warmupJob.await()
            }
        } catch (e: CancellationException) {
            throw IllegalStateException("Prompt embedding generation was cancelled", e)
        }

        return rulePromptEmbeddings[preprocessed]
            ?: throw IllegalArgumentException("Embeddings for prompt: '$prompt' were not configured")
    }

    override fun close() {
        warmupJob.cancel()
        scope.cancel()
    }

    private suspend fun generateAndCacheEmbeddings(
        onnxSessionFactory: OnnxSessionFactory,
        ortEnvironment: OrtEnvironment,
        prompts: List<String>,
    ) {
        tokenizerFactory().use { tokenizer ->
            logger.info("Generating embeddings for ${prompts.size} prompts")
            onnxSessionFactory.create(EmbeddingModel.SIGLIP2_BASE_PATCH16_224_TEXT).use { session ->
                prompts
                    .chunked(EMBEDDING_BATCH_SIZE)
                    .forEach { batch ->
                        val timed =
                            measureTimedValue {
                                generateBatch(
                                    ortEnvironment = ortEnvironment,
                                    ortSession = session,
                                    tokenizer = tokenizer,
                                    prompts = batch,
                                )
                            }

                        rulePromptEmbeddings.putAll(timed.value)
                        timed.value.forEach { (prompt, embedding) ->
                            embeddingCacheRepository.store(
                                embeddingModel = EmbeddingModel.SIGLIP2_BASE_PATCH16_224_TEXT,
                                prompt = prompt,
                                embeddings = embedding,
                            )
                        }
                        logger.info(
                            "Generated embeddings for ${batch.size} prompts in ${timed.duration.inWholeMilliseconds}ms",
                        )
                    }
            }
        }
    }

    private fun generateBatch(
        ortEnvironment: OrtEnvironment,
        ortSession: OrtSession,
        tokenizer: Siglip2Tokenizer,
        prompts: List<String>,
    ): Map<String, FloatArray> {
        val encoded = prompts.zip(tokenizer.encodeBatch(prompts))
        val sequenceLength =
            encoded
                .first()
                .second.inputIds.size

        encoded.forEach { (prompt, tokens) ->
            require(tokens.inputIds.size == sequenceLength) {
                "Prompt '$prompt' tokenized to ${tokens.inputIds.size} tokens, expected $sequenceLength"
            }
            require(tokens.attentionMask.size == sequenceLength) {
                "Prompt '$prompt' attention mask had ${tokens.attentionMask.size} tokens, expected $sequenceLength"
            }
        }

        val inputIds = LongArray(encoded.size * sequenceLength)
        val attentionMask = LongArray(encoded.size * sequenceLength)

        encoded.forEachIndexed { row, (_, tokens) ->
            tokens.inputIds.copyInto(
                destination = inputIds,
                destinationOffset = row * sequenceLength,
            )
            tokens.attentionMask.copyInto(
                destination = attentionMask,
                destinationOffset = row * sequenceLength,
            )
        }

        val inputIdsTensor =
            OnnxTensor.createTensor(
                ortEnvironment,
                LongBuffer.wrap(inputIds),
                longArrayOf(encoded.size.toLong(), sequenceLength.toLong()),
            )
        val attentionMaskTensor =
            OnnxTensor.createTensor(
                ortEnvironment,
                LongBuffer.wrap(attentionMask),
                longArrayOf(encoded.size.toLong(), sequenceLength.toLong()),
            )

        inputIdsTensor.use {
            attentionMaskTensor.use {
                val inputs =
                    buildMap {
                        put(INPUT_IDS, inputIdsTensor)
                        if (ortSession.inputNames.contains(ATTENTION_MASK)) {
                            put(ATTENTION_MASK, attentionMaskTensor)
                        }
                    }

                ortSession.run(inputs).use { outputs ->
                    val embeddings =
                        extractPooledEmbeddings(
                            outputs = outputs,
                            primaryOutputName = TEXT_EMBEDS,
                            modelDescription = "Text model",
                        )

                    check(embeddings.size == prompts.size) {
                        "Text model returned ${embeddings.size} embeddings for ${prompts.size} prompts"
                    }

                    return prompts
                        .zip(embeddings)
                        .associate { (prompt, embedding) ->
                            prompt to embedding.l2Normalize()
                        }
                }
            }
        }
    }

    private fun preprocessPrompt(prompt: String): String = prompt.trim().lowercase().prependIfMissing(PROMPT_PREFIX)

    private fun String.prependIfMissing(prefix: String): String = if (startsWith(prefix)) this else prefix + this
}
