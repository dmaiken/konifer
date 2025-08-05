package image.model

import asset.model.LQIPResponse
import io.asset.ManipulationParameters.HEIGHT
import io.asset.ManipulationParameters.MIME_TYPE
import io.asset.ManipulationParameters.WIDTH
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.tryGetString
import io.properties.ValidatedProperties
import kotlinx.serialization.Serializable

data class ProcessedImage(
    val attributes: ImageAttributes,
    val lqip: LQIPs,
)

data class RequestedImageAttributes(
    val width: Int?,
    val height: Int?,
    val mimeType: String?,
) : ValidatedProperties {
    companion object Factory {
        val ORIGINAL_VARIANT =
            RequestedImageAttributes(
                width = null,
                height = null,
                mimeType = null,
            )

        fun create(applicationConfig: ApplicationConfig): RequestedImageAttributes =
            RequestedImageAttributes(
                width = applicationConfig.tryGetString(WIDTH)?.toInt(),
                height = applicationConfig.tryGetString(HEIGHT)?.toInt(),
                mimeType = applicationConfig.tryGetString(MIME_TYPE),
            ).apply {
                validate()
            }
    }

    fun isOriginalVariant(): Boolean {
        return width == null && height == null && mimeType == null
    }

    override fun validate() {
        if (width != null && width < 1) {
            throw IllegalArgumentException("Width cannot be < 1")
        }
        if (height != null && height < 1) {
            throw IllegalArgumentException("Height cannot be < 1")
        }
        if (mimeType != null) {
            ImageFormat.fromMimeType(mimeType)
        }
    }
}

data class ImageAttributes(
    val width: Int,
    val height: Int,
    val mimeType: String,
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
