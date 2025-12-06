package io.direkt.infrastructure.vips

import org.koin.core.module.Module
import org.koin.dsl.module

fun vipsModule(): Module =
    module {
        single<VipsImageProcessor> {
            VipsImageProcessor()
        }
    }
