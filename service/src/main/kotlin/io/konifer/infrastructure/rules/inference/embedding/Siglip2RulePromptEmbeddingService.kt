package io.konifer.infrastructure.rules.inference.embedding

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import io.konifer.domain.rules.RuleDefinition
import io.konifer.domain.rules.RulePrompt
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.LongBuffer
import kotlin.time.measureTimedValue

class Siglip2RulePromptEmbeddingService(
    private val ortEnvironment: OrtEnvironment,
    onnxSessionFactory: OnnxSessionFactory,
    ruleDefinitions: List<RuleDefinition>,
    private val embeddingCacheRepository: EmbeddingCacheRepository,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val tokenizerFactory: () -> Siglip2Tokenizer = ::Siglip2Tokenizer,
    private val allowEmbeddingCacheMiss: Boolean = true,
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
    private val ortSession =
        lazy {
            if (allowEmbeddingCacheMiss) {
                onnxSessionFactory.create(EmbeddingModel.SIGLIP2_BASE_PATCH16_224_TEXT)
            } else {
                null
            }
        }
    private val tokenizer =
        lazy {
            if (allowEmbeddingCacheMiss) tokenizerFactory() else null
        }

    private val embeddingGenerationMutex = Mutex()

    private val warmupJob =
        scope.async {
            val promptKeys =
                ruleDefinitions
                    .flatMap { it.prompts }
                    .map(::toPromptEmbeddingKey)
                    .distinctBy { it.cacheKey }

            if (promptKeys.isEmpty()) return@async

            // fetch cached embeddings and see if we even need the session/tokenizer
            val cached =
                embeddingCacheRepository.fetch(
                    embeddingModel = EmbeddingModel.SIGLIP2_BASE_PATCH16_224_TEXT,
                    prompts = promptKeys.map { it.cacheKey },
                )
            val promptsRequiringGeneration = promptKeys.filterNot { cached.containsKey(it.cacheKey) }
            if (promptsRequiringGeneration.isNotEmpty()) {
                val tokenizer = tokenizer.value ?: tokenizerFactory()
                val session = ortSession.value ?: onnxSessionFactory.create(EmbeddingModel.SIGLIP2_BASE_PATCH16_224_TEXT)
                generateAndCacheEmbeddings(
                    ortEnvironment = ortEnvironment,
                    prompts = promptsRequiringGeneration.map { it.cacheKey },
                    session = session,
                    tokenizer = tokenizer,
                    terminateEmbeddingResources = !allowEmbeddingCacheMiss,
                )
            } else {
                logger.info("All prompts found in embedding cache, skipping siglip2 text model initialization")
            }
        }

    override fun generateEmbeddings(prompts: List<RulePrompt>): Map<RulePrompt, FloatArray> =
        runBlocking {
            val promptKeys = prompts.map(::toPromptEmbeddingKey)
            if (promptKeys.isEmpty()) return@runBlocking emptyMap()

            awaitWarmup()

            val distinctCacheKeys = promptKeys.map { it.cacheKey }.distinct()
            val cachedPrompts =
                embeddingCacheRepository.fetch(
                    prompts = distinctCacheKeys,
                    embeddingModel = EmbeddingModel.SIGLIP2_BASE_PATCH16_224_TEXT,
                )

            val missingPrompts =
                distinctCacheKeys
                    .filterNot { cachedPrompts.containsKey(it) }

            val generatedPrompts =
                if (missingPrompts.isEmpty()) {
                    emptyMap()
                } else {
                    if (!allowEmbeddingCacheMiss) {
                        throw IllegalArgumentException(
                            "Embeddings for prompts were not configured: ${missingPrompts.joinToString()}",
                        )
                    }
                    embeddingGenerationMutex.withLock {
                        val refreshedPrompts =
                            embeddingCacheRepository.fetch(
                                prompts = missingPrompts,
                                embeddingModel = EmbeddingModel.SIGLIP2_BASE_PATCH16_224_TEXT,
                            )
                        val stillMissingPrompts = missingPrompts.filterNot { refreshedPrompts.containsKey(it) }
                        if (stillMissingPrompts.isEmpty()) {
                            return@withLock refreshedPrompts
                        }

                        val session =
                            checkNotNull(ortSession.value) {
                                "Text model session was not initialized for prompt embedding cache misses"
                            }
                        val tokenizer =
                            checkNotNull(tokenizer.value) {
                                "Tokenizer was not initialized for prompt embedding cache misses"
                            }

                        refreshedPrompts +
                            generateAndCacheEmbeddings(
                                ortEnvironment = ortEnvironment,
                                prompts = stillMissingPrompts,
                                session = session,
                                tokenizer = tokenizer,
                                terminateEmbeddingResources = false,
                            )
                    }
                }

            val embeddingsByCacheKey = cachedPrompts + generatedPrompts
            promptKeys.associate { promptKey ->
                promptKey.rulePrompt to
                    checkNotNull(embeddingsByCacheKey[promptKey.cacheKey]) {
                        "Embeddings for prompt '${promptKey.rulePrompt.prompt}' were not generated"
                    }
            }
        }

    override fun close() {
        warmupJob.cancel()
        scope.cancel()
        if (ortSession.isInitialized()) {
            ortSession.value?.close()
        }
        if (tokenizer.isInitialized()) {
            tokenizer.value?.close()
        }
    }

    private fun awaitWarmup() {
        try {
            runBlocking {
                warmupJob.await()
            }
        } catch (e: CancellationException) {
            throw IllegalStateException("Prompt embedding generation was cancelled", e)
        }
    }

    private suspend fun generateAndCacheEmbeddings(
        ortEnvironment: OrtEnvironment,
        prompts: List<String>,
        tokenizer: Siglip2Tokenizer,
        session: OrtSession,
        terminateEmbeddingResources: Boolean,
    ): Map<String, FloatArray> {
        try {
            logger.info("Generating embeddings for ${prompts.size} prompts")
            val promptEmbeddings = mutableMapOf<String, FloatArray>()
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

                    embeddingCacheRepository.storeAll(
                        embeddingModel = EmbeddingModel.SIGLIP2_BASE_PATCH16_224_TEXT,
                        prompts = timed.value,
                    )
                    promptEmbeddings.putAll(timed.value)

                    logger.info(
                        "Generated embeddings for ${batch.size} prompts in ${timed.duration.inWholeMilliseconds}ms",
                    )
                }

            return promptEmbeddings
        } finally {
            if (terminateEmbeddingResources) {
                session.close()
                tokenizer.close()
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

    private fun toPromptEmbeddingKey(rulePrompt: RulePrompt): PromptEmbeddingKey =
        PromptEmbeddingKey(
            rulePrompt = rulePrompt,
            cacheKey = preprocessPrompt(rulePrompt.prompt),
        )

    private fun preprocessPrompt(prompt: String): String = prompt.trim().lowercase().prependIfMissing(PROMPT_PREFIX)

    private fun String.prependIfMissing(prefix: String): String = if (startsWith(prefix)) this else prefix + this

    private data class PromptEmbeddingKey(
        val rulePrompt: RulePrompt,
        val cacheKey: String,
    )
}
