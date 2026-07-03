package io.konifer.infrastructure.rules

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import io.konifer.domain.ports.RuleDefinitionRepository
import io.konifer.domain.rules.RuleDefinition
import io.konifer.infrastructure.rules.inference.InferenceRuleEvaluator
import io.konifer.infrastructure.rules.inference.Siglip2LogitSimilarityScorer
import io.konifer.infrastructure.rules.inference.Siglip2ModelFiles
import io.konifer.infrastructure.rules.inference.Siglip2Tokenizer
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
import kotlin.io.path.pathString
import org.koin.core.module.dsl.bind as bindType

fun rulesModule(ruleDefinitions: Map<String, RuleDefinition>): Module =
    module {
        single<RuleDefinitionRepository> {
            ConfigurationRuleDefinitionRepository(ruleDefinitions)
        }
        single<Siglip2LogitSimilarityScorer>() bind SimilarityScorer::class
        single<InferenceRuleEvaluator>() bind RuleEvaluator::class

        single<Siglip2Tokenizer>()

        val ortEnvironment = OrtEnvironment.getEnvironment()
        single<Siglip2ContentEmbeddingService> {
            val session =
                ortEnvironment.createSession(
                    Siglip2ModelFiles.visionModel().pathString,
                    OrtSession.SessionOptions(),
                )
            Siglip2ContentEmbeddingService(
                ortEnvironment = ortEnvironment,
                ortSession = session,
            )
        } withOptions {
            bindType<ContentEmbeddingService>()
            createdAtStart()
            onClose { service -> service?.close() }
        }
        single<RulePromptEmbeddingService> {
            ortEnvironment
                .createSession(
                    Siglip2ModelFiles.textModel().pathString,
                    OrtSession.SessionOptions(),
                ).use { session ->
                    Siglip2RulePromptEmbeddingService(
                        ortEnvironment = ortEnvironment,
                        ortSession = session,
                        ruleDefinitions = ruleDefinitions.values.toList(),
                        tokenizer = get(),
                    )
                }
        } withOptions {
            createdAtStart()
        }
    }
