package image

import io.asset.ImageAttributeAdapter
import io.image.hash.ImagePreviewGenerator
import org.koin.core.module.Module
import org.koin.dsl.module

fun imageModule(): Module =
    module {
        single<VipsImageProcessor> {
            VipsImageProcessor(get())
        }

        single<ImageAttributeAdapter> {
            ImageAttributeAdapter()
        }

        single<ImagePreviewGenerator> {
            ImagePreviewGenerator()
        }
    }
