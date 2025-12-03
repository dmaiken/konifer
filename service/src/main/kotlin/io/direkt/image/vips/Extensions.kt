package io.image.vips

import app.photofox.vipsffm.VImage
import io.image.vips.VipsOptionNames.OPTION_PAGE_HEIGHT

fun VImage.aspectRatio(): Double = this.width.toDouble() / this.height.toDouble()

fun VImage.pageSafeHeight(): Int = this.getInt(OPTION_PAGE_HEIGHT) ?: this.height
