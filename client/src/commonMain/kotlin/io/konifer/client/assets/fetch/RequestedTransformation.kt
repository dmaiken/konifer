package io.konifer.client.assets.fetch

import io.konifer.common.image.Filter
import io.konifer.common.image.Fit
import io.konifer.common.image.Flip
import io.konifer.common.image.Gravity
import io.konifer.common.image.ImageFormat
import io.konifer.common.image.MetadataType
import io.konifer.common.image.Rotate
import io.konifer.common.image.TransformableColorSpace
import kotlin.jvm.JvmField

/** Creates an immutable image transformation from the assignments in [block]. */
inline fun requestedTransformation(block: RequestedTransformationDsl.() -> Unit): RequestedTransformation =
    RequestedTransformationDsl().apply(block).build()

/** Kotlin DSL for building a [RequestedTransformation]. Unset properties use server defaults. */
class RequestedTransformationDsl {
    /** Requested output width in pixels. */
    var width: Int? = null

    /** Requested output height in pixels. */
    var height: Int? = null

    /** Requested output image format. */
    var format: ImageFormat? = null

    /** Resize strategy used with the requested dimensions. */
    var fit: Fit? = null

    /** Alignment used by transformations that crop or pad the image. */
    var gravity: Gravity? = null

    /** Requested image rotation. */
    var rotate: Rotate? = null

    /** Requested resampling filter. */
    var filter: Filter? = null

    /** Requested image reflection. */
    var flip: Flip? = null

    /** Gaussian blur amount. */
    var blur: Int? = null

    /** Output encoder quality. */
    var quality: Int? = null

    /** Padding added around the transformed image, in pixels. */
    var pad: Int? = null

    /** Padding color accepted by the Konifer server. */
    var padColor: String? = null

    /** Name of a server-configured variant profile. */
    var profile: String? = null

    /** Requested output color space. */
    var colorSpace: TransformableColorSpace? = null

    private val strip: MutableSet<MetadataType> = mutableSetOf()

    /** Adds metadata categories to remove from the output image. */
    fun strip(vararg types: MetadataType) {
        strip += types
    }

    /** Creates an immutable transformation from the current DSL values. */
    fun build(): RequestedTransformation =
        RequestedTransformation(
            width = width,
            height = height,
            format = format,
            fit = fit,
            gravity = gravity,
            rotate = rotate,
            filter = filter,
            flip = flip,
            blur = blur,
            quality = quality,
            pad = pad,
            padColor = padColor,
            profile = profile,
            strip = strip.toSet(),
            colorSpace = colorSpace,
        )
}

/**
 * Immutable image transformations encoded into an asset variant request.
 *
 * Create one with [requestedTransformation] from Kotlin or [Builder] from Java.
 * `null` properties leave that transformation unspecified.
 *
 * @property width requested output width in pixels.
 * @property height requested output height in pixels.
 * @property format requested output image format.
 * @property fit resize strategy used with the requested dimensions.
 * @property gravity alignment used by crop and padding operations.
 * @property rotate requested image rotation.
 * @property filter requested resampling filter.
 * @property flip requested image reflection.
 * @property blur Gaussian blur amount.
 * @property quality output encoder quality.
 * @property pad padding added around the transformed image, in pixels.
 * @property padColor padding color accepted by the Konifer server.
 * @property profile name of a server-configured variant profile.
 * @property strip metadata categories removed from the output image.
 * @property colorSpace requested output color space.
 */
class RequestedTransformation internal constructor(
    val width: Int?,
    val height: Int?,
    val format: ImageFormat?,
    val fit: Fit?,
    val gravity: Gravity?,
    val rotate: Rotate?,
    val filter: Filter?,
    val flip: Flip?,
    val blur: Int?,
    val quality: Int?,
    val pad: Int?,
    val padColor: String?,
    val profile: String?,
    val strip: Set<MetadataType>,
    val colorSpace: TransformableColorSpace?,
) {
    companion object {
        /** A request for the stored image without transformations. */
        @JvmField
        val OriginalVariant: RequestedTransformation = Builder().build()
    }

    /** Java-friendly builder for [RequestedTransformation]. */
    class Builder {
        private var width: Int? = null

        /** Sets the requested output width in pixels. */
        fun width(width: Int): Builder = apply { this.width = width }

        private var height: Int? = null

        /** Sets the requested output height in pixels. */
        fun height(height: Int): Builder = apply { this.height = height }

        private var format: ImageFormat? = null

        /** Sets the requested output image format. */
        fun format(format: ImageFormat): Builder = apply { this.format = format }

        private var fit: Fit? = null

        /** Sets the resize strategy used with the requested dimensions. */
        fun fit(fit: Fit): Builder = apply { this.fit = fit }

        private var gravity: Gravity? = null

        /** Sets the alignment used by crop and padding operations. */
        fun gravity(gravity: Gravity): Builder = apply { this.gravity = gravity }

        private var rotate: Rotate? = null

        /** Sets the requested image rotation. */
        fun rotate(rotate: Rotate): Builder = apply { this.rotate = rotate }

        private var filter: Filter? = null

        /** Sets the requested resampling filter. */
        fun filter(filter: Filter): Builder = apply { this.filter = filter }

        private var flip: Flip? = null

        /** Sets the requested image reflection. */
        fun flip(flip: Flip): Builder = apply { this.flip = flip }

        private var blur: Int? = null

        /** Sets the Gaussian blur amount. */
        fun blur(blur: Int): Builder = apply { this.blur = blur }

        private var quality: Int? = null

        /** Sets the output encoder quality. */
        fun quality(quality: Int): Builder = apply { this.quality = quality }

        private var pad: Int? = null

        /** Sets the padding around the transformed image, in pixels. */
        fun pad(pad: Int): Builder = apply { this.pad = pad }

        private var padColor: String? = null

        /** Sets the padding color accepted by the Konifer server. */
        fun padColor(padColor: String): Builder = apply { this.padColor = padColor }

        private var profile: String? = null

        /** Sets the name of a server-configured variant profile. */
        fun profile(profile: String): Builder = apply { this.profile = profile }

        private val strip = mutableSetOf<MetadataType>()

        /** Adds metadata categories to remove from the output image. */
        fun strip(vararg types: MetadataType): Builder = apply { strip.addAll(types) }

        private var colorSpace: TransformableColorSpace? = null

        /** Sets the requested output color space. */
        fun colorSpace(colorSpace: TransformableColorSpace): Builder = apply { this.colorSpace = colorSpace }

        /** Creates an immutable transformation from the current builder values. */
        fun build(): RequestedTransformation =
            RequestedTransformation(
                width = width,
                height = height,
                format = format,
                fit = fit,
                gravity = gravity,
                rotate = rotate,
                filter = filter,
                flip = flip,
                blur = blur,
                quality = quality,
                pad = pad,
                padColor = padColor,
                profile = profile,
                strip = strip.toSet(),
                colorSpace = colorSpace,
            )
    }
}
