package io.konifer.client

import io.konifer.common.image.Filter
import io.konifer.common.image.Fit
import io.konifer.common.image.Gravity
import io.konifer.common.image.ImageFormat
import io.konifer.common.image.Rotate
import kotlin.jvm.JvmField

inline fun requestedTransformation(block: RequestedTransformation.Builder.() -> Unit): RequestedTransformation =
    RequestedTransformation.Builder().apply(block).build()

class RequestedTransformation private constructor(
    val width: Int?,
    val height: Int?,
    val format: ImageFormat?,
    val fit: Fit?,
    val gravity: Gravity?,
    val rotate: Rotate?,
    val filter: Filter?,
    val blur: Int?,
    val quality: Int?,
    val pad: Int?,
    val padColor: String?,
) {
    companion object {
        @JvmField
        val OriginalVariant: RequestedTransformation = Builder().build()
    }
    // Traditional Builder for Java interoperability
    class Builder {
        private var width: Int? = null

        fun width(width: Int) = apply { this.width = width }

        private var height: Int? = null

        fun height(height: Int) = apply { this.height = height }

        private var format: ImageFormat? = null

        fun format(format: ImageFormat) = apply { this.format = format }

        private var fit: Fit? = null

        fun fit(fit: Fit) = apply { this.fit = fit }

        private var gravity: Gravity? = null

        fun gravity(gravity: Gravity) = apply { this.gravity = gravity }

        private var rotate: Rotate? = null

        fun rotate(rotate: Rotate) = apply { this.rotate = rotate }

        private var filter: Filter? = null

        fun filter(filter: Filter) = apply { this.filter = filter }

        private var blur: Int? = null

        fun blur(blur: Int) = apply { this.blur = blur }

        private var quality: Int? = null

        fun quality(quality: Int) = apply { this.quality = quality }

        private var pad: Int? = null

        fun pad(pad: Int) = apply { this.pad = pad }

        private var padColor: String? = null

        fun padColor(padColor: String) = apply { this.padColor = padColor }

        fun build() =
            RequestedTransformation(
                width = width,
                height = height,
                format = format,
                fit = fit,
                gravity = gravity,
                rotate = rotate,
                filter = filter,
                blur = blur,
                quality = quality,
                pad = pad,
                padColor = padColor,
            )
    }
}
