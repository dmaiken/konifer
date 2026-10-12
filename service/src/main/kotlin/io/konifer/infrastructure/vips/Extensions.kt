package io.konifer.infrastructure.vips

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.VipsHelper
import app.photofox.vipsffm.enums.VipsBandFormat
import app.photofox.vipsffm.enums.VipsInteresting
import io.konifer.common.image.Gravity
import io.konifer.common.image.pixel.PixelDataType
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_PAGE_HEIGHT

fun VImage.aspectRatio(): Double = this.width.toDouble() / this.height.toDouble()

fun VImage.pageSafeHeight(): Int = this.getInt(OPTION_PAGE_HEIGHT) ?: this.height

fun VImage.unPremultiplyIfNecessary(isAlphaPremultiplied: Boolean): VImage =
    if (isAlphaPremultiplied) {
        this.unpremultiply()
    } else {
        this
    }

fun VImage.premultiplyIfNecessary(isAlphaPremultiplied: Boolean): Pair<VImage, Boolean> =
    if (!isAlphaPremultiplied && this.hasAlpha()) {
        Pair(this.premultiply(), true)
    } else {
        Pair(this, isAlphaPremultiplied)
    }

fun VImage.interpretation(): Int = VipsHelper.image_get_interpretation(this.unsafeStructAddress)

fun VImage.format(): VipsBandFormat =
    VipsHelper.image_get_format(this.unsafeStructAddress).let { raw ->
        VipsBandFormat.entries.first { it.rawValue == raw }
    }

fun Gravity.toVipsInteresting(): VipsInteresting =
    when (this) {
        Gravity.ATTENTION -> VipsInteresting.INTERESTING_ATTENTION
        Gravity.CENTER -> VipsInteresting.INTERESTING_CENTRE
        Gravity.ENTROPY -> VipsInteresting.INTERESTING_ENTROPY
    }

fun VipsBandFormat.toPixelDataType(): PixelDataType =
    when (this) {
        VipsBandFormat.FORMAT_UCHAR -> PixelDataType.UINT8
        VipsBandFormat.FORMAT_CHAR -> PixelDataType.INT8
        VipsBandFormat.FORMAT_USHORT -> PixelDataType.UINT16
        VipsBandFormat.FORMAT_SHORT -> PixelDataType.INT16
        VipsBandFormat.FORMAT_UINT -> PixelDataType.UINT32
        VipsBandFormat.FORMAT_INT -> PixelDataType.INT32
        VipsBandFormat.FORMAT_FLOAT -> PixelDataType.FLOAT32
        VipsBandFormat.FORMAT_DOUBLE -> PixelDataType.FLOAT64
        VipsBandFormat.FORMAT_COMPLEX -> PixelDataType.COMPLEX64
        VipsBandFormat.FORMAT_DPCOMPLEX -> PixelDataType.COMPLEX128
        VipsBandFormat.FORMAT_NOTSET -> throw IllegalArgumentException("Pixel format not set!")
    }
