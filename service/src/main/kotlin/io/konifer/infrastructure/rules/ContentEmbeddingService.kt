package io.konifer.infrastructure.rules

import io.konifer.common.image.Fit
import io.konifer.common.image.ImageFormat
import io.konifer.common.image.Rotate
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.variant.Transformation

class ContentEmbeddingService {

    private val embeddingTransformation = Transformation(
        width = 224,
        height = 224,
        fit = Fit.STRETCH,
        rotate = Rotate.AUTO,
        format = ImageFormat.PNG,
        colorSpace = ColorSpace.SRGB,
    )
}
