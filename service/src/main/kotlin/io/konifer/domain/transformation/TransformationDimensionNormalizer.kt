package io.konifer.domain.transformation

import io.konifer.common.image.Fit
import io.konifer.common.image.Rotate
import io.konifer.domain.context.RequestedTransformation
import io.konifer.domain.variant.Attributes
import kotlinx.coroutines.Deferred
import kotlin.math.roundToInt

object TransformationDimensionNormalizer {
    suspend fun normalizeDimensions(
        requested: RequestedTransformation,
        normalizedRotate: Rotate,
        originalAttributesDeferred: Deferred<Attributes>,
    ): Pair<Int, Int> =
        when (requested.fit) {
            Fit.FIT -> {
                if (requested.width == null || requested.height == null) {
                    val originalVariant = originalAttributesDeferred.await()

                    // Requested dimensions describe the oriented output, not the source before rotation.
                    val (orientedWidth, orientedHeight) =
                        orientHeightAndWidth(
                            originalVariantAttributes = originalVariant,
                            normalizedRotate = normalizedRotate,
                        )

                    when {
                        requested.width != null ->
                            Pair(
                                requested.width,
                                ((orientedHeight * requested.width) / orientedWidth).roundToInt(),
                            )
                        requested.height != null ->
                            Pair(
                                ((orientedWidth * requested.height) / orientedHeight).roundToInt(),
                                requested.height,
                            )
                        else -> Pair(orientedWidth.roundToInt(), orientedHeight.roundToInt())
                    }
                } else {
                    Pair(requested.width, requested.height)
                }
            }
            Fit.FILL, Fit.STRETCH, Fit.CROP -> {
                Pair(requireNotNull(requested.width), requireNotNull(requested.height))
            }
        }

    private fun orientHeightAndWidth(
        normalizedRotate: Rotate,
        originalVariantAttributes: Attributes,
    ): Pair<Double, Double> =
        if (normalizedRotate == Rotate.NINETY || normalizedRotate == Rotate.TWO_HUNDRED_SEVENTY) {
            Pair(originalVariantAttributes.height.toDouble(), originalVariantAttributes.width.toDouble())
        } else {
            Pair(originalVariantAttributes.width.toDouble(), originalVariantAttributes.height.toDouble())
        }
}
