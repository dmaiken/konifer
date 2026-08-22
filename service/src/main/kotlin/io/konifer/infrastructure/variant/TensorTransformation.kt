package io.konifer.infrastructure.variant

import io.konifer.common.image.Fit
import io.konifer.common.image.Gravity
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.transformation.Dimension
import io.konifer.domain.transformation.toDimension

sealed interface TensorTransformation {
    val height: Dimension
    val width: Dimension
    val fit: Fit
    val gravity: Gravity
    val colorSpace: ColorSpace
    val tensorLayout: LongArray
}

object Siglip2TensorTransformation : TensorTransformation {
    override val height: Dimension = 224.toDimension()
    override val width: Dimension = 224.toDimension()
    override val fit: Fit = Fit.FILL
    override val gravity: Gravity = Gravity.CENTER
    override val colorSpace: ColorSpace = ColorSpace.SRGB

    override val tensorLayout: LongArray = longArrayOf(1, 3, 224, 224)
}
