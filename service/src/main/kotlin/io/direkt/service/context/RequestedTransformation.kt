package io.direkt.service.context

import io.direkt.domain.image.Filter
import io.direkt.domain.image.Fit
import io.direkt.domain.image.Flip
import io.direkt.domain.image.Gravity
import io.direkt.domain.image.ImageFormat
import io.direkt.domain.image.Rotate
import io.direkt.infrastructure.properties.ValidatedProperties
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
) : ValidatedProperties {
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
                format = applicationConfig.tryGetString(ManipulationParameters.MIME_TYPE)?.let { ImageFormat.fromMimeType(it) },
                fit = Fit.fromString(applicationConfig.tryGetString(ManipulationParameters.FIT)),
                gravity = Gravity.fromString(applicationConfig.tryGetString(ManipulationParameters.GRAVITY)),
                rotate = Rotate.fromString(applicationConfig.tryGetString(ManipulationParameters.ROTATE)),
                flip = Flip.fromString(applicationConfig.tryGetString(ManipulationParameters.FLIP)),
                filter = Filter.fromString(applicationConfig.tryGetString(ManipulationParameters.FILTER)),
                blur = applicationConfig.tryGetString(ManipulationParameters.BLUR)?.toInt(),
                quality = applicationConfig.tryGetString(ManipulationParameters.QUALITY)?.toInt(),
                pad = applicationConfig.tryGetString(ManipulationParameters.PAD)?.toInt(),
                background = applicationConfig.tryGetString(ManipulationParameters.BACKGROUND),
            ).apply {
                validate()
            }
    }

    override fun validate() {
        if (originalVariant) {
            return
        }
        if (width != null && width < 1) {
            throw IllegalArgumentException("Width cannot be < 1")
        }
        if (height != null && height < 1) {
            throw IllegalArgumentException("Height cannot be < 1")
        }
        when (fit) {
            Fit.FIT -> {}
            Fit.FILL, Fit.STRETCH, Fit.CROP -> {
                if (height == null || width == null) {
                    throw IllegalArgumentException("Height or width must be supplied for fit: $fit")
                }
            }
        }
        if (blur != null && (blur !in 0..150)) {
            throw IllegalArgumentException("Blur must be between 0 and 150")
        }
        if (quality != null && (quality !in 1..100)) {
            throw IllegalArgumentException("Quality must be between 1 and 100")
        }
        if (pad != null && pad < 0) {
            throw IllegalArgumentException("Pad must not be negative")
        }
        if (background != null && (!background.startsWith('#') || background.drop(1).toLongOrNull(16) == null)) {
            throw IllegalArgumentException("Background must be a hex value starting with '#'")
        }
    }
}
