package image.model

import asset.model.LQIPResponse
import io.asset.ManipulationParameters.FIT
import io.asset.ManipulationParameters.HEIGHT
import io.asset.ManipulationParameters.MIME_TYPE
import io.asset.ManipulationParameters.WIDTH
import io.image.model.Fit
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.tryGetString
import io.properties.ValidatedProperties
import kotlinx.serialization.Serializable

data class ProcessedImage(
    val attributes: ImageAttributes,
    val lqip: LQIPs,
)

data class RequestedImageAttributes(
    val originalVariant: Boolean = false,
    val width: Int?,
    val height: Int?,
    val format: ImageFormat?,
    val fit: Fit,
) : ValidatedProperties {
    companion object Factory {
        val ORIGINAL_VARIANT =
            RequestedImageAttributes(
                originalVariant = true,
                width = null,
                height = null,
                format = null,
                fit = Fit.SCALE,
            )

        fun create(applicationConfig: ApplicationConfig): RequestedImageAttributes =
            RequestedImageAttributes(
                originalVariant = false,
                width = applicationConfig.tryGetString(WIDTH)?.toInt(),
                height = applicationConfig.tryGetString(HEIGHT)?.toInt(),
                format = applicationConfig.tryGetString(MIME_TYPE)?.let { ImageFormat.fromMimeType(it) },
                fit = Fit.fromString(applicationConfig.tryGetString(FIT)),
            ).apply {
                validate()
            }
    }

    fun isOriginalVariant(): Boolean {
        return width == null && height == null && format == null
    }

    override fun validate() {
        if (width != null && width < 1) {
            throw IllegalArgumentException("Width cannot be < 1")
        }
        if (height != null && height < 1) {
            throw IllegalArgumentException("Height cannot be < 1")
        }
    }
}

data class ImageAttributes(
    val width: Int,
    val height: Int,
    val fit: Fit = Fit.SCALE,
    val format: ImageFormat,
)

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
