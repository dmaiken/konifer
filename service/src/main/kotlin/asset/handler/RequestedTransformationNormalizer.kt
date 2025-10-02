package io.asset.handler

import asset.repository.AssetRepository
import image.model.ImageFormat
import image.model.RequestedImageTransformation
import image.model.Transformation
import io.asset.variant.ImageVariantAttributes
import io.image.model.ExifOrientations.normalizeOrientation
import io.image.model.Fit
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
                    )?.getOriginalVariant()?.attributes ?: throw IllegalArgumentException(
                        "Original variant not found with path: $treePath, entryId: ${entryId ?: "Not Specified"}",
                    )
                }

            doNormalize(
                requested = requested,
                originalAttributesDeferred = originalVariantDeferred,
            )
        }

    suspend fun normalize(
        requested: List<RequestedImageTransformation>,
        originalVariantAttributes: ImageVariantAttributes,
    ): List<Transformation> =
        coroutineScope {
            if (requested.isEmpty()) {
                return@coroutineScope emptyList()
            }

            requested.map { request ->
                doNormalize(
                    requested = request,
                    originalAttributesDeferred =
                        async {
                            originalVariantAttributes
                        },
                )
            }
        }

    suspend fun normalize(
        requested: RequestedImageTransformation,
        originalVariantAttributes: ImageVariantAttributes,
    ): Transformation =
        coroutineScope {
            doNormalize(
                requested = requested,
                originalAttributesDeferred =
                    async {
                        originalVariantAttributes
                    },
            )
        }

    private suspend fun doNormalize(
        requested: RequestedImageTransformation,
        originalAttributesDeferred: Deferred<ImageVariantAttributes>,
    ): Transformation {
        if (requested.originalVariant) {
            return Transformation.ORIGINAL_VARIANT
        }
        val (width, height) = normalizeDimensions(requested, originalAttributesDeferred)
        val (rotate, horizontalFlip) = normalizeOrientation(requested.rotate, requested.flip)

        return Transformation(
            width = width,
            height = height,
            fit = requested.fit,
            format = normalizeFormat(requested, originalAttributesDeferred),
            rotate = rotate,
            horizontalFlip = horizontalFlip,
        ).also {
            // Cancel coroutine if we never used it and it's not in progress
            if (!originalAttributesDeferred.isActive && !originalAttributesDeferred.isCompleted) {
                originalAttributesDeferred.cancel()
            }
            logger.info("Normalized requested transformation: $requested to: $it")
        }
    }

    private suspend fun normalizeDimensions(
        requested: RequestedImageTransformation,
        originalVariantDeferred: Deferred<ImageVariantAttributes>,
    ): Pair<Int, Int> {
        return when (requested.fit) {
            Fit.SCALE -> {
                if ((requested.width == null && requested.height != null) || (requested.width != null && requested.height == null)) {
                    val originalVariant = originalVariantDeferred.await()

                    val originalWidth = originalVariant.width.toDouble()
                    val originalHeight = originalVariant.height.toDouble()
                    // Derive height/width if needed
                    Pair(
                        requested.width ?: ((originalWidth * requireNotNull(requested.height)) / originalHeight).roundToInt(),
                        requested.height ?: ((originalHeight * requireNotNull(requested.width)) / originalWidth).roundToInt(),
                    )
                } else if (requested.height != null && requested.width != null) {
                    Pair(requested.width, requested.height)
                } else {
                    Pair(
                        originalVariantDeferred.await().width,
                        originalVariantDeferred.await().height,
                    )
                }
            }
            Fit.FIT, Fit.STRETCH -> {
                Pair(requireNotNull(requested.width), requireNotNull(requested.height))
            }
        }
    }

    private suspend fun normalizeFormat(
        requested: RequestedImageTransformation,
        originalVariantDeferred: Deferred<ImageVariantAttributes>,
    ): ImageFormat = requested.format ?: originalVariantDeferred.await().format
}
