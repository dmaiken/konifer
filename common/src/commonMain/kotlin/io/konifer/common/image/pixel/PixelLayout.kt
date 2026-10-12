package io.konifer.common.image.pixel

import io.konifer.common.image.Manipulation
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private object PixelLayoutParameterValues {
    const val INTERLEAVED = "interleaved"
    const val PLANAR = "planar"
}

@Serializable
enum class PixelLayout(
    override val queryParameterValue: String,
) : Manipulation {
    /**
     * Planar pixel layout. `RRRGGGBBB` or whatever pixel-channel was specified.
     */
    @SerialName(PixelLayoutParameterValues.PLANAR)
    PLANAR(PixelLayoutParameterValues.PLANAR),

    /**
     * Interleaved pixel layout (default). Pixels are arranged in `RGBRGBRGB` or whatever pixel-channel was specified.
     */
    @SerialName(PixelLayoutParameterValues.INTERLEAVED)
    INTERLEAVED(PixelLayoutParameterValues.INTERLEAVED), ;

    companion object Factory {
        val default = INTERLEAVED
    }
}
