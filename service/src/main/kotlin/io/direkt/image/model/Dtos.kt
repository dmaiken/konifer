package io.direkt.image.model

import io.direkt.asset.ManipulationParameters.BACKGROUND
import io.direkt.asset.ManipulationParameters.BLUR
import io.direkt.asset.ManipulationParameters.FILTER
import io.direkt.asset.ManipulationParameters.FIT
import io.direkt.asset.ManipulationParameters.FLIP
import io.direkt.asset.ManipulationParameters.GRAVITY
import io.direkt.asset.ManipulationParameters.HEIGHT
import io.direkt.asset.ManipulationParameters.MIME_TYPE
import io.direkt.asset.ManipulationParameters.PAD
import io.direkt.asset.ManipulationParameters.QUALITY
import io.direkt.asset.ManipulationParameters.ROTATE
import io.direkt.asset.ManipulationParameters.WIDTH
import io.direkt.asset.model.LQIPResponse
import io.direkt.properties.ValidatedProperties
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.tryGetString
import kotlinx.serialization.Serializable
import java.io.File

data class ProcessedImage(
    val result: File,
    val attributes: Attributes,
    val transformation: Transformation,
    val lqip: LQIPs,
)

data class PreProcessedImage(
    val result: File,
    val attributes: Attributes,
    val lqip: LQIPs,
)

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
                width = applicationConfig.tryGetString(WIDTH)?.toInt(),
                height = applicationConfig.tryGetString(HEIGHT)?.toInt(),
                format = applicationConfig.tryGetString(MIME_TYPE)?.let { ImageFormat.fromMimeType(it) },
                fit = Fit.fromString(applicationConfig.tryGetString(FIT)),
                gravity = Gravity.fromString(applicationConfig.tryGetString(GRAVITY)),
                rotate = Rotate.fromString(applicationConfig.tryGetString(ROTATE)),
                flip = Flip.fromString(applicationConfig.tryGetString(FLIP)),
                filter = Filter.fromString(applicationConfig.tryGetString(FILTER)),
                blur = applicationConfig.tryGetString(BLUR)?.toInt(),
                quality = applicationConfig.tryGetString(QUALITY)?.toInt(),
                pad = applicationConfig.tryGetString(PAD)?.toInt(),
                background = applicationConfig.tryGetString(BACKGROUND),
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

data class Transformation(
    val originalVariant: Boolean = false,
    val width: Int,
    val height: Int,
    val fit: Fit = Fit.default,
    val gravity: Gravity = Gravity.default,
    val canUpscale: Boolean = true,
    val format: ImageFormat,
    /**
     * Ignored if [rotate] is [Rotate.AUTO]
     */
    val rotate: Rotate = Rotate.default,
    val horizontalFlip: Boolean = false,
    val filter: Filter = Filter.default,
    val blur: Int = 0,
    val quality: Int = format.vipsProperties.defaultQuality,
    val pad: Int = 0,
    /**
     * Background will be a 4-element list representing RGBA
     */
    val background: List<Int> = emptyList(),
) {
    companion object Factory {
        val ORIGINAL_VARIANT =
            Transformation(
                originalVariant = true,
                width = 1,
                height = 1,
                format = ImageFormat.PNG,
            )
    }
}

@Serializable
data class LQIPs(
    val blurhash: String?,
    val thumbhash: String?,
) {
    companion object Factory {
        val NONE = LQIPs(null, null)
    }

    fun toResponse(): LQIPResponse =
        LQIPResponse(
            blurhash = blurhash,
            thumbhash = thumbhash,
        )
}
