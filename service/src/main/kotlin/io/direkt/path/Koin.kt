package io.direkt.path

import io.direkt.path.configuration.PathConfigurationRepository
import io.ktor.server.application.Application
import org.koin.core.module.Module
import org.koin.dsl.module

fun Application.pathModule(): Module =
    module {
        single<PathConfigurationRepository> {
            PathConfigurationRepository(environment.config)
        }
    }
