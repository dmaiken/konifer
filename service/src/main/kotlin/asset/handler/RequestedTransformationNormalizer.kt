package io.asset.handler

import asset.model.AssetAndVariants
import asset.repository.AssetRepository
import image.model.ImageFormat
import image.model.RequestedImageTransformation
import image.model.Transformation
import io.image.model.Fit
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.debug
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.math.roundToInt

class RequestedTransformationNormalizer(
    private val assetRepository: AssetRepository
) {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    /**
     * Transform the supplied [RequestedImageTransformation] into a normalized [Transformation] where every transformation
     * attribute is specified, if needed, by deriving missing values using the original variant.
     */
    suspend fun normalize(
        treePath: String,
        entryId: Long?,
        requested: RequestedImageTransformation?
    ): Transformation = coroutineScope {
        if (requested == null) {
            logger.debug { "Requested original variant for path: $treePath, entryId: ${entryId ?: "Not specified"}" }
            return@coroutineScope Transformation.ORIGINAL_VARIANT
        }

        val originalVariantDeferred = async(start = CoroutineStart.LAZY) {
            assetRepository.fetchByPath(
                path = treePath,
                entryId = entryId,
                transformation = Transformation.ORIGINAL_VARIANT
            ) ?: throw IllegalArgumentException("Original variant not found with path: $treePath, entryId: ${entryId ?: "Not Specified"}")
        }

        val (width, height) = normalizeDimensions(requested, originalVariantDeferred)

        Transformation(
            width = width,
            height = height,
            fit = requested.fit,
            format = normalizeFormat(requested, originalVariantDeferred)
        ).also {
            logger.debug { "Normalized requested transformation: $requested to: $it" }
        }
    }

    private suspend fun normalizeDimensions(requested: RequestedImageTransformation, originalVariantDeferred: Deferred<AssetAndVariants>): Pair<Int, Int> {
        return if (requested.fit == Fit.SCALE && (requested.width != null || requested.height != null)) {
            val originalVariant = originalVariantDeferred.await()

            val originalWidth = originalVariant.getOriginalVariant().attributes.width.toDouble()
            val originalHeight = originalVariant.getOriginalVariant().attributes.height.toDouble()
            // Derive height/width if needed
            Pair(
                requested.width ?: ((originalWidth * checkNotNull(requested.height)) / originalHeight).roundToInt(),
                requested.height ?: ((originalHeight * checkNotNull(requested.width)) / originalWidth).roundToInt()
            )
        } else {
            Pair(checkNotNull(requested.width), checkNotNull(requested.height))
        }
    }

    private suspend fun normalizeFormat(requested: RequestedImageTransformation, originalVariantDeferred: Deferred<AssetAndVariants>): ImageFormat =
        requested.format ?: originalVariantDeferred.await().getOriginalVariant().attributes.format
}