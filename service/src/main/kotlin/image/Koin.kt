package image

import io.asset.ImageAttributeAdapter
import org.koin.core.module.Module
import org.koin.dsl.module

fun imageModule(): Module =
    module {
        single<ImageProcessor> {
            VipsImageProcessor()
        }

        single<ImageAttributeAdapter> {
            ImageAttributeAdapter()
        }
    }
