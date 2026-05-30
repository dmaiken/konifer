package io.konifer.infrastructure.vips

import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

fun vipsModule(): Module =
    module {
        single<VipsImageProcessor>()
    }
