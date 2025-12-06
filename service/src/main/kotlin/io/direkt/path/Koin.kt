package io.direkt.path

import io.direkt.domain.ports.PathConfigurationRepository
import io.direkt.infrastructure.path.TriePathConfigurationRepository
import io.ktor.server.application.Application
import org.koin.core.module.Module
import org.koin.dsl.module

fun Application.pathModule(): Module =
    module {
        single<PathConfigurationRepository> {
            TriePathConfigurationRepository(environment.config)
        }
    }
