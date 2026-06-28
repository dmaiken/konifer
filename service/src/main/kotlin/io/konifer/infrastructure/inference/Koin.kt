package io.konifer.infrastructure.inference

import io.konifer.infrastructure.inference.embedding.ContentEmbeddingService
import io.konifer.infrastructure.inference.embedding.Siglip2ContentEmbeddingService
import io.ktor.server.application.Application
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

fun Application.inferenceModule() =
    module {
        single<Siglip2ContentEmbeddingService>() bind ContentEmbeddingService::class
        single<Siglip2Tokenizer>()
    }
