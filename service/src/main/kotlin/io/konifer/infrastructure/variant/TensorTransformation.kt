package io.konifer.infrastructure.variant

import io.konifer.common.image.Fit
import io.konifer.common.image.Gravity
import io.konifer.domain.image.ColorSpace

sealed interface TensorTransformation {
    val height: Int
    val width: Int
    val fit: Fit
    val gravity: Gravity
    val colorSpace: ColorSpace
    val tensorLayout: LongArray
}

object Siglip2TensorTransformation : TensorTransformation {
    override val height: Int = 224
    override val width: Int = 224
    override val fit: Fit = Fit.FILL
    override val gravity: Gravity = Gravity.CENTER
    override val colorSpace: ColorSpace = ColorSpace.SRGB

    override val tensorLayout: LongArray = longArrayOf(1, 3, 224, 224)
}
