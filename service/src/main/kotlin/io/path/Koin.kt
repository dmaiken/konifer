package io.path

import io.ktor.server.application.Application
import io.path.configuration.PathConfigurationRepository
import org.koin.core.module.Module
import org.koin.dsl.module

fun Application.pathModule(): Module =
    module {
        single<PathConfigurationRepository> {
            PathConfigurationRepository(environment.config)
        }
    }
