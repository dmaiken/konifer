package io.konifer.domain.transformation

import io.konifer.common.image.Flip
import io.konifer.common.image.ManipulationParameters
import io.konifer.common.image.Rotate
import io.konifer.domain.context.RequestedTransformation
import io.konifer.domain.image.ExifOrientations
import io.konifer.domain.variant.Attributes
import kotlinx.coroutines.Deferred

object RotateFlipNormalizer {
    suspend fun normalizeRotateFlip(
        requested: RequestedTransformation,
        originalAttributesDeferred: Deferred<Attributes>,
    ): RotateFlipParameters =
        if (requested.rotate == Rotate.AUTO) {
            if (requested.flip != Flip.NONE) {
                throw IllegalArgumentException(
                    "Cannot specify flip (${ManipulationParameters.FLIP}) when r=${Rotate.AUTO.name.lowercase()}",
                )
            }
            ExifOrientations.fromExifOrientation(originalAttributesDeferred.await().orientation)
        } else {
            ExifOrientations.normalizeOrientation(requested.rotate, requested.flip)
        }.let { (rotate, horizontalFlip) ->
            RotateFlipParameters(
                rotate = rotate,
                horizontalFlip = horizontalFlip,
                isAutoRotate = requested.rotate == Rotate.AUTO,
            )
        }
}

data class RotateFlipParameters(
    val rotate: Rotate,
    val horizontalFlip: Boolean,
    val isAutoRotate: Boolean,
)
