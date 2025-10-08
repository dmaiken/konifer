package image.model

import asset.model.LQIPResponse
import io.asset.ManipulationParameters.BLUR
import io.asset.ManipulationParameters.FILTER
import io.asset.ManipulationParameters.FIT
import io.asset.ManipulationParameters.FLIP
import io.asset.ManipulationParameters.GRAVITY
import io.asset.ManipulationParameters.HEIGHT
import io.asset.ManipulationParameters.MIME_TYPE
import io.asset.ManipulationParameters.QUALITY
import io.asset.ManipulationParameters.ROTATE
import io.asset.ManipulationParameters.WIDTH
import io.image.model.Filter
import io.image.model.Fit
import io.image.model.Flip
import io.image.model.Gravity
import io.image.model.Rotate
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.tryGetString
import io.properties.ValidatedProperties
import kotlinx.serialization.Serializable

data class ProcessedImage(
    val attributes: Attributes,
    val transformation: Transformation,
    val lqip: LQIPs,
)

data class PreProcessedImage(
    val attributes: Attributes,
    val lqip: LQIPs,
)

data class RequestedImageTransformation(
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
) : ValidatedProperties {
    companion object Factory {
        val ORIGINAL_VARIANT =
            RequestedImageTransformation(
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
            )

        fun create(applicationConfig: ApplicationConfig): RequestedImageTransformation =
            RequestedImageTransformation(
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
    }
}

data class Attributes(
    val width: Int,
    val height: Int,
    val format: ImageFormat,
)

data class Transformation(
    val originalVariant: Boolean = false,
    val width: Int,
    val height: Int,
    val fit: Fit = Fit.default,
    val gravity: Gravity = Gravity.default,
    val format: ImageFormat,
    val rotate: Rotate = Rotate.default,
    val horizontalFlip: Boolean = false,
    val filter: Filter = Filter.default,
    val blur: Int = 0,
    val quality: Int = format.vipsProperties.defaultQuality,
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
