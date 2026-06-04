package io.konifer.domain.context

import io.konifer.common.image.Filter
import io.konifer.common.image.Fit
import io.konifer.common.image.Flip
import io.konifer.common.image.Gravity
import io.konifer.common.image.ImageFormat
import io.konifer.common.image.ManipulationParameters
import io.konifer.common.image.MetadataType
import io.konifer.common.image.Rotate
import io.konifer.common.image.TransformableColorSpace
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class RequestedTransformation(
    @Transient
    val originalVariant: Boolean = false,
    @SerialName(ManipulationParameters.WIDTH)
    val width: Int? = null,
    @SerialName(ManipulationParameters.HEIGHT)
    val height: Int? = null,
    @SerialName(ManipulationParameters.FORMAT)
    val format: ImageFormat? = null,
    @SerialName(ManipulationParameters.FIT)
    val fit: Fit = Fit.default,
    @SerialName(ManipulationParameters.GRAVITY)
    val gravity: Gravity = Gravity.default,
    @SerialName(ManipulationParameters.ROTATE)
    val rotate: Rotate = Rotate.default,
    @SerialName(ManipulationParameters.FLIP)
    val flip: Flip = Flip.default,
    @Transient
    val canUpscale: Boolean = true,
    @SerialName(ManipulationParameters.FILTER)
    val filter: Filter = Filter.default,
    @SerialName(ManipulationParameters.BLUR)
    val blur: Int? = null,
    @SerialName(ManipulationParameters.QUALITY)
    val quality: Int? = null,
    @SerialName(ManipulationParameters.PAD)
    val pad: Int? = null,
    @SerialName(ManipulationParameters.PAD_COLOR)
    val padColor: String? = null,
    @SerialName(ManipulationParameters.STRIP)
    val stripMetadata: String? = null,
    @SerialName(ManipulationParameters.COLOR_SPACE)
    val colorSpace: TransformableColorSpace = TransformableColorSpace.default,
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
                padColor = null,
                stripMetadata = null,
                colorSpace = TransformableColorSpace.default,
            )
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
        if (padColor != null) {
            require(padColor.startsWith('#') && padColor.drop(1).toLongOrNull(16) != null) {
                "Pad color must be a hex value starting with '#'"
            }
        }
        if (stripMetadata != null) {
            val validMetadata = MetadataType.entries.map { it.name }
            stripMetadata
                .split(",")
                .filter { it.isNotBlank() }
                .forEach { value ->
                    require(value.uppercase() in validMetadata) {
                        "Invalid metadata type: $value. Valid types are: ${validMetadata.joinToString(", ")}"
                    }
                }
        }
    }
}
