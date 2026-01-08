package io.direkt.infrastructure.vips

import app.photofox.vipsffm.VImage
import io.direkt.infrastructure.vips.VipsOptionNames.OPTION_PAGE_HEIGHT

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
