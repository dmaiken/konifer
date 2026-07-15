package io.konifer.infrastructure.vips

import io.konifer.infrastructure.vips.processor.VipsImageProcessor
import io.konifer.infrastructure.vips.processor.VipsTensorProcessor
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

fun vipsModule(): Module =
    module {
        single<VipsImageProcessor>()
        single<VipsTensorProcessor>()
    }
