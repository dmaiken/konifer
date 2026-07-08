package io.konifer.infrastructure.rules

import ai.onnxruntime.OrtEnvironment
import io.konifer.domain.ports.RuleDefinitionRepository
import io.konifer.domain.rules.RuleDefinition
import io.konifer.infrastructure.rules.inference.InferenceRuleEvaluator
import io.konifer.infrastructure.rules.inference.OnnxSessionFactory
import io.konifer.infrastructure.rules.inference.Siglip2LogitSimilarityScorer
import io.konifer.infrastructure.rules.inference.SimilarityScorer
import io.konifer.infrastructure.rules.inference.embedding.ContentEmbeddingService
import io.konifer.infrastructure.rules.inference.embedding.RulePromptEmbeddingService
import io.konifer.infrastructure.rules.inference.embedding.Siglip2ContentEmbeddingService
import io.konifer.infrastructure.rules.inference.embedding.Siglip2RulePromptEmbeddingService
import org.koin.core.module.Module
import org.koin.core.module.dsl.createdAtStart
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single
import org.koin.core.module.dsl.bind as bindType

fun rulesModule(ruleDefinitions: Map<String, RuleDefinition>): Module =
    module {
        single<RuleDefinitionRepository> {
            ConfigurationRuleDefinitionRepository(ruleDefinitions)
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
                ruleDefinitions = ruleDefinitions.values.toList(),
                scope = get(),
            )
        } withOptions {
            createdAtStart()
            onClose { service -> (service as? AutoCloseable)?.close() }
        }
    }
