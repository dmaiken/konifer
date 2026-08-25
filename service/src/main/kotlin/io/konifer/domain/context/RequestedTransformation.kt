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
import io.konifer.domain.transformation.Blur
import io.konifer.domain.transformation.Dimension
import io.konifer.domain.transformation.PaddingAmount
import io.konifer.domain.transformation.Quality
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class RequestedTransformation(
    @Transient
    val originalVariant: Boolean = false,
    @SerialName(ManipulationParameters.WIDTH)
    val width: Dimension? = null,
    @SerialName(ManipulationParameters.HEIGHT)
    val height: Dimension? = null,
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
    val blur: Blur? = null,
    @SerialName(ManipulationParameters.QUALITY)
    val quality: Quality? = null,
    @SerialName(ManipulationParameters.PAD)
    val pad: PaddingAmount? = null,
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
            )
    }

    /** Validates relationships and structured values that are not owned by the numeric value classes. */
    private fun validate() {
        if (originalVariant) {
            return
        }
        when (fit) {
            Fit.FIT -> {}
            Fit.FILL, Fit.STRETCH, Fit.CROP -> {
                require(height != null && width != null) {
                    "Height or width must be supplied for fit: $fit"
                }
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
                    require(value.trim().uppercase() in validMetadata) {
                        "Invalid metadata type: $value. Valid types are: ${validMetadata.joinToString(", ")}"
                    }
                }
        }
    }
}
