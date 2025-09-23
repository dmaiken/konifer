package io.asset.handler

import asset.model.AssetAndVariants
import asset.repository.AssetRepository
import image.model.ImageFormat
import image.model.RequestedImageTransformation
import image.model.Transformation
import io.image.model.ExifOrientations
import io.image.model.Fit
import io.image.model.Flip
import io.image.model.Rotate
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.debug
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.math.roundToInt

class RequestedTransformationNormalizer(
    private val assetRepository: AssetRepository,
) {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    /**
     * Transform the supplied [RequestedImageTransformation] into a normalized [Transformation] where every transformation
     * attribute is specified, if needed, by deriving missing values using the original variant.
     */
    suspend fun normalize(
        treePath: String,
        entryId: Long?,
        requested: RequestedImageTransformation,
    ): Transformation =
        coroutineScope {
            if (requested.originalVariant) {
                logger.debug { "Requested original variant for path: $treePath, entryId: ${entryId ?: "Not specified"}" }
                return@coroutineScope Transformation.ORIGINAL_VARIANT
            }

            val originalVariantDeferred =
                async(start = CoroutineStart.LAZY) {
                    assetRepository.fetchByPath(
                        path = treePath,
                        entryId = entryId,
                        transformation = Transformation.ORIGINAL_VARIANT,
                    ) ?: throw IllegalArgumentException(
                        "Original variant not found with path: $treePath, entryId: ${entryId ?: "Not Specified"}",
                    )
                }

            doNormalize(
                requested = requested,
                originalVariantDeferred = originalVariantDeferred,
            )
        }

    suspend fun normalize(
        requested: List<RequestedImageTransformation>,
        originalAsset: AssetAndVariants,
    ): List<Transformation> =
        coroutineScope {
            if (requested.isEmpty()) {
                logger.debug { "Requested original variant for path: ${originalAsset.asset.path}, entryId: ${originalAsset.asset.entryId}" }
                return@coroutineScope emptyList()
            }

            requested.map { request ->
                doNormalize(
                    requested = request,
                    originalVariantDeferred =
                        async {
                            originalAsset
                        },
                )
            }
        }

    private suspend fun doNormalize(
        requested: RequestedImageTransformation,
        originalVariantDeferred: Deferred<AssetAndVariants>,
    ): Transformation {
        if (requested.originalVariant) {
            return Transformation.ORIGINAL_VARIANT
        }
        val (width, height) = normalizeDimensions(requested, originalVariantDeferred)
        val (rotate, horizontalFlip) = normalizeRotation(requested.rotate, requested.flip)

        return Transformation(
            width = width,
            height = height,
            fit = requested.fit,
            format = normalizeFormat(requested, originalVariantDeferred),
            rotate = rotate,
            horizontalFlip = horizontalFlip
        ).also {
            // Cancel coroutine if we never used it and it's not in progress
            if (!originalVariantDeferred.isActive && !originalVariantDeferred.isCompleted) {
                originalVariantDeferred.cancel()
            }
            logger.info("Normalized requested transformation: $requested to: $it")
        }
    }

    private suspend fun normalizeDimensions(
        requested: RequestedImageTransformation,
        originalVariantDeferred: Deferred<AssetAndVariants>,
    ): Pair<Int, Int> {
        return when (requested.fit) {
            Fit.SCALE -> {
                if ((requested.width == null && requested.height != null) || (requested.width != null && requested.height == null)) {
                    val originalVariant = originalVariantDeferred.await()

                    val originalWidth = originalVariant.getOriginalVariant().attributes.width.toDouble()
                    val originalHeight = originalVariant.getOriginalVariant().attributes.height.toDouble()
                    // Derive height/width if needed
                    Pair(
                        requested.width ?: ((originalWidth * requireNotNull(requested.height)) / originalHeight).roundToInt(),
                        requested.height ?: ((originalHeight * requireNotNull(requested.width)) / originalWidth).roundToInt(),
                    )
                } else if (requested.height != null && requested.width != null) {
                    Pair(requested.width, requested.height)
                } else {
                    Pair(
                        originalVariantDeferred.await().getOriginalVariant().attributes.width,
                        originalVariantDeferred.await().getOriginalVariant().attributes.height,
                    )
                }
            }
            Fit.FIT, Fit.STRETCH -> {
                Pair(requireNotNull(requested.width), requireNotNull(requested.height))
            }
        }
    }

    /**
     * Normalize the rotation and flip from (clockwise [Rotate], [Flip]) to (clockwise [Rotate], [Boolean] horizontal flip)
     */
    private fun normalizeRotation(rotate: Rotate = Rotate.default, flip: Flip = Flip.default): Pair<Rotate, Boolean> {
        return if (rotate == Rotate.ZERO && flip == Flip.NONE) {
            ExifOrientations.ONE
        } else if ((rotate == Rotate.ONE_HUNDRED_EIGHTY && flip == Flip.H) || (rotate == Rotate.ZERO && flip == Flip.V)) {
            ExifOrientations.TWO
        } else if (rotate == Rotate.ONE_HUNDRED_EIGHTY && flip == Flip.NONE) {
            ExifOrientations.THREE
        } else if ((rotate == Rotate.ZERO && flip == Flip.H) || (rotate == Rotate.ONE_HUNDRED_EIGHTY && flip == Flip.V)) {
            ExifOrientations.FOUR
        } else if ((rotate == Rotate.TWO_HUNDRED_SEVENTY && flip == Flip.H) || (rotate == Rotate.NINETY && flip == Flip.V)) {
            ExifOrientations.FIVE
        } else if (rotate == Rotate.TWO_HUNDRED_SEVENTY && flip == Flip.NONE) {
            ExifOrientations.SIX
        } else if ((rotate == Rotate.NINETY && flip == Flip.H) || (rotate == Rotate.TWO_HUNDRED_SEVENTY && flip == Flip.V)) {
            ExifOrientations.SEVEN
        } else if (rotate == Rotate.NINETY && flip == Flip.NONE) {
            ExifOrientations.EIGHT
        } else {
            throw IllegalArgumentException("Rotation not supported: $rotate, Flip: $flip")
        }
    }

    private suspend fun normalizeFormat(
        requested: RequestedImageTransformation,
        originalVariantDeferred: Deferred<AssetAndVariants>,
    ): ImageFormat = requested.format ?: originalVariantDeferred.await().getOriginalVariant().attributes.format
}
