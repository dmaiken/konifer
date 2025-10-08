package image

import io.image.lqip.ImagePreviewGenerator
import io.image.vips.VipsEncoder
import org.koin.core.module.Module
import org.koin.dsl.module

fun imageModule(): Module =
    module {
        single<VipsImageProcessor> {
            VipsImageProcessor(get(), get(), get())
        }

        single<ImagePreviewGenerator> {
            ImagePreviewGenerator()
        }

        single<VipsEncoder> {
            VipsEncoder()
        }
    }
