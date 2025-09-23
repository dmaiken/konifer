package image.model

import asset.model.LQIPResponse
import io.asset.ManipulationParameters.FIT
import io.asset.ManipulationParameters.FLIP
import io.asset.ManipulationParameters.HEIGHT
import io.asset.ManipulationParameters.MIME_TYPE
import io.asset.ManipulationParameters.ROTATE
import io.asset.ManipulationParameters.WIDTH
import io.image.model.Fit
import io.image.model.Flip
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
    val rotate: Rotate,
    val flip: Flip,
    val canUpscale: Boolean = true
) : ValidatedProperties {
    companion object Factory {
        val ORIGINAL_VARIANT =
            RequestedImageTransformation(
                originalVariant = true,
                width = null,
                height = null,
                format = null,
                fit = Fit.default,
                rotate = Rotate.default,
                flip = Flip.default,
            )

        fun create(applicationConfig: ApplicationConfig): RequestedImageTransformation =
            RequestedImageTransformation(
                originalVariant = false,
                width = applicationConfig.tryGetString(WIDTH)?.toInt(),
                height = applicationConfig.tryGetString(HEIGHT)?.toInt(),
                format = applicationConfig.tryGetString(MIME_TYPE)?.let { ImageFormat.fromMimeType(it) },
                fit = Fit.fromString(applicationConfig.tryGetString(FIT)),
                rotate = Rotate.fromString(applicationConfig.tryGetString(ROTATE)),
                flip = Flip.fromString(applicationConfig.tryGetString(FLIP)),
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
            Fit.SCALE -> {
                return
            }
            Fit.FIT, Fit.STRETCH -> {
                if (height == null || width == null) {
                    throw IllegalArgumentException("Height or width must be supplied for fit: $fit")
                }
            }
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
    val format: ImageFormat,
    val rotate: Rotate = Rotate.default,
    val horizontalFlip: Boolean = false,
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
