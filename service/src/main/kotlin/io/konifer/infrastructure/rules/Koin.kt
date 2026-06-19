package io.konifer.infrastructure.rules

import io.konifer.domain.ports.RuleDefinitionRepository
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

fun rulesModule(): Module =
    module {
        single<ConfigurationRuleDefinitionRepository>() bind RuleDefinitionRepository::class
    }
