package io.konifer.infrastructure.rules

import ai.onnxruntime.OrtEnvironment
import io.konifer.application.usecase.evaluate.EvaluateRuleDefinitionUseCase
import io.konifer.domain.ports.RuleDefinitionRepository
import io.konifer.domain.ports.RuleEvaluationProcessor
import io.konifer.domain.rules.RuleDefinition
import io.konifer.infrastructure.datastore.DataStoreProvider
import io.konifer.infrastructure.rules.evaluate.ChannelRuleEvaluationProcessor
import io.konifer.infrastructure.rules.inference.InferenceRuleEvaluator
import io.konifer.infrastructure.rules.inference.OnnxSessionFactory
import io.konifer.infrastructure.rules.inference.Siglip2LogitSimilarityScorer
import io.konifer.infrastructure.rules.inference.SimilarityScorer
import io.konifer.infrastructure.rules.inference.embedding.ContentEmbeddingService
import io.konifer.infrastructure.rules.inference.embedding.EmbeddingCacheRepository
import io.konifer.infrastructure.rules.inference.embedding.NoOpEmbeddingCacheRepository
import io.konifer.infrastructure.rules.inference.embedding.PostgresEmbeddingCacheRepository
import io.konifer.infrastructure.rules.inference.embedding.RulePromptEmbeddingService
import io.konifer.infrastructure.rules.inference.embedding.Siglip2ContentEmbeddingService
import io.konifer.infrastructure.rules.inference.embedding.Siglip2RulePromptEmbeddingService
import io.konifer.infrastructure.work.synchronousChannel
import org.koin.core.module.Module
import org.koin.core.module.dsl.createdAtStart
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single
import org.koin.core.module.dsl.bind as bindType

fun rulesModule(
    ruleDefinitions: List<RuleDefinition>,
    dataStoreProvider: DataStoreProvider,
    shouldEnableEvaluationApi: Boolean,
): Module =
    module {
        single<RuleDefinitionRepository> {
            ConfigurationRuleDefinitionRepository(ruleDefinitions)
        }
        when (dataStoreProvider) {
            DataStoreProvider.IN_MEMORY -> single<NoOpEmbeddingCacheRepository>() bind EmbeddingCacheRepository::class
            DataStoreProvider.POSTGRES -> single<PostgresEmbeddingCacheRepository>() bind EmbeddingCacheRepository::class
        }

        single<Siglip2LogitSimilarityScorer>() bind SimilarityScorer::class
        single<InferenceRuleEvaluator>() bind RuleEvaluator::class
        val ortEnvironment = OrtEnvironment.getEnvironment()
        single<OnnxSessionFactory> {
            OnnxSessionFactory(ortEnvironment)
        } withOptions {
            createdAtStart()
        }
        single<Siglip2ContentEmbeddingService> {
            Siglip2ContentEmbeddingService(
                ortEnvironment = ortEnvironment,
                onnxSessionFactory = get(),
            )
        } withOptions {
            bindType<ContentEmbeddingService>()
            createdAtStart()
            onClose { service -> service?.close() }
        }
        single<RulePromptEmbeddingService> {
            Siglip2RulePromptEmbeddingService(
                ortEnvironment = ortEnvironment,
                onnxSessionFactory = get(),
                ruleDefinitions = ruleDefinitions,
                embeddingCacheRepository = get(),
            )
        } withOptions {
            createdAtStart()
            onClose { service -> (service as? AutoCloseable)?.close() }
        }
        if (shouldEnableEvaluationApi) {
            single<EvaluateRuleDefinitionUseCase>()
            single<ChannelRuleEvaluationProcessor> {
                ChannelRuleEvaluationProcessor(
                    highPriorityChannel = get(synchronousChannel),
                )
            } bind RuleEvaluationProcessor::class
        }
    }
