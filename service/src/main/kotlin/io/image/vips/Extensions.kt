package io.image.vips

import app.photofox.vipsffm.VImage

fun VImage.aspectRatio(): Double = this.width.toDouble() / this.height.toDouble()
