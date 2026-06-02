package io.konifer.infrastructure.path

import io.konifer.domain.ports.PathConfigurationRepository
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

fun pathModule(): Module =
    module {
        single<TriePathConfigurationRepository>() bind PathConfigurationRepository::class
    }
