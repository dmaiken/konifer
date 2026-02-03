package io.konifer.service.context

import io.konifer.domain.image.Filter
import io.konifer.domain.image.Fit
import io.konifer.domain.image.Flip
import io.konifer.domain.image.Gravity
import io.konifer.domain.image.ImageFormat
import io.konifer.domain.image.Rotate
import io.konifer.service.context.selector.ManipulationParameters
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.tryGetString

data class RequestedTransformation(
    val originalVariant: Boolean = false,
    val width: Int?,
    val height: Int?,
    val format: ImageFormat?,
    val fit: Fit,
    val gravity: Gravity,
    val rotate: Rotate,
    val flip: Flip,
    val canUpscale: Boolean = true,
    val filter: Filter,
    val blur: Int?,
    val quality: Int?,
    val pad: Int?,
    val background: String?,
) {
    init {
        validate()
    }

    companion object Factory {
        val ORIGINAL_VARIANT =
            RequestedTransformation(
                originalVariant = true,
                width = null,
                height = null,
                format = null,
                fit = Fit.default,
                gravity = Gravity.default,
                rotate = Rotate.default,
                flip = Flip.default,
                filter = Filter.default,
                blur = null,
                quality = null,
                pad = null,
                background = null,
            )

        fun create(applicationConfig: ApplicationConfig): RequestedTransformation =
            RequestedTransformation(
                originalVariant = false,
                width = applicationConfig.tryGetString(ManipulationParameters.WIDTH)?.toInt(),
                height = applicationConfig.tryGetString(ManipulationParameters.HEIGHT)?.toInt(),
                format = applicationConfig.tryGetString(ManipulationParameters.FORMAT)?.let { ImageFormat.fromFormat(it) },
                fit = Fit.fromString(applicationConfig.tryGetString(ManipulationParameters.FIT)),
                gravity = Gravity.fromString(applicationConfig.tryGetString(ManipulationParameters.GRAVITY)),
                rotate = Rotate.fromString(applicationConfig.tryGetString(ManipulationParameters.ROTATE)),
                flip = Flip.fromString(applicationConfig.tryGetString(ManipulationParameters.FLIP)),
                filter = Filter.fromString(applicationConfig.tryGetString(ManipulationParameters.FILTER)),
                blur = applicationConfig.tryGetString(ManipulationParameters.BLUR)?.toInt(),
                quality = applicationConfig.tryGetString(ManipulationParameters.QUALITY)?.toInt(),
                pad = applicationConfig.tryGetString(ManipulationParameters.PAD)?.toInt(),
                background = applicationConfig.tryGetString(ManipulationParameters.PAD_COLOR),
            ).apply {
                validate()
            }
    }

    private fun validate() {
        if (originalVariant) {
            return
        }
        if (width != null) {
            require(width >= 1) {
                "Width cannot be < 1"
            }
        }
        if (height != null) {
            require(height >= 1) {
                "Height cannot be < 1"
            }
        }
        when (fit) {
            Fit.FIT -> {}
            Fit.FILL, Fit.STRETCH, Fit.CROP -> {
                require(height != null && width != null) {
                    "Height or width must be supplied for fit: $fit"
                }
            }
        }
        if (blur != null) {
            require(blur in 0..150) {
                "Blur must be between 0 and 150"
            }
        }
        if (quality != null) {
            require(quality in 1..100) {
                "Quality must be between 1 and 100"
            }
        }
        if (pad != null) {
            require(pad >= 0) {
                "Pad must not be negative"
            }
        }
        if (background != null) {
            require(background.startsWith('#') && background.drop(1).toLongOrNull(16) != null) {
                "Background must be a hex value starting with '#'"
            }
        }
    }
}
