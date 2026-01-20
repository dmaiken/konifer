package io.konifer.infrastructure.path

import io.konifer.domain.ports.PathConfigurationRepository
import io.ktor.server.application.Application
import org.koin.core.module.Module
import org.koin.dsl.module

fun Application.pathModule(): Module =
    module {
        single<PathConfigurationRepository> {
            TriePathConfigurationRepository(environment.config)
        }
    }
