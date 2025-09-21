package image

import io.image.lqip.ImagePreviewGenerator
import org.koin.core.module.Module
import org.koin.dsl.module

fun imageModule(): Module =
    module {
        single<VipsImageProcessor> {
            VipsImageProcessor(get())
        }

        single<ImagePreviewGenerator> {
            ImagePreviewGenerator()
        }
    }
