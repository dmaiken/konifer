package io.konifer.infrastructure.health

import io.ktor.server.application.Application
import org.koin.core.module.Module
import org.koin.core.module.dsl.createdAtStart
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.bind
import org.koin.dsl.module

fun Application.healthModule(): Module =
    module {
        single<KtorHealthIndicator> {
            KtorHealthIndicator(monitor = monitor)
        } withOptions {
            createdAtStart()
        } bind HealthIndicator::class

        single {
            KoniferHealthIndicator(
                indicators = getAll<HealthIndicator>(),
            )
        }
    }
