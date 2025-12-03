package io.image

import io.image.vips.VipsImageProcessor
import org.koin.core.module.Module
import org.koin.dsl.module

fun imageModule(): Module =
    module {
        single<VipsImageProcessor> {
            VipsImageProcessor()
        }
    }
