package io.konifer.service.transformation

import io.konifer.domain.image.ExifOrientations
import io.konifer.domain.image.Fit
import io.konifer.domain.image.Flip
import io.konifer.domain.image.ImageFormat
import io.konifer.domain.image.Rotate
import io.konifer.domain.ports.AssetRepository
import io.konifer.domain.variant.Attributes
import io.konifer.domain.variant.Padding
import io.konifer.domain.variant.Transformation
import io.konifer.service.context.RequestedTransformation
import io.konifer.service.context.selector.ManipulationParameters
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.debug
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.math.roundToInt

class TransformationNormalizer(
    private val assetRepository: AssetRepository,
) {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    /**
     * Transform the supplied [RequestedTransformation] into a normalized [Transformation] where every transformation
     * attribute is specified, if needed, by deriving missing values using the original variant.
     */
    suspend fun normalize(
        treePath: String,
        entryId: Long?,
        requested: RequestedTransformation,
    ): Transformation =
        coroutineScope {
            if (requested.originalVariant) {
                logger.debug { "Requested original variant for path: $treePath, entryId: ${entryId ?: "Not specified"}" }
                return@coroutineScope Transformation.ORIGINAL_VARIANT
            }

            val originalVariantDeferred =
                async(start = CoroutineStart.LAZY) {
                    assetRepository
                        .fetchByPath(
                            path = treePath,
                            entryId = entryId,
                            transformation = Transformation.ORIGINAL_VARIANT,
                            includeOnlyReady = false,
                        )?.variants
                        ?.firstOrNull { it.isOriginalVariant }
                        ?.attributes ?: throw IllegalArgumentException(
                        "Original variant not found with path: $treePath, entryId: ${entryId ?: "Not Specified"}",
                    )
                }

            doNormalize(
                requested = requested,
                originalAttributesDeferred = originalVariantDeferred,
            )
        }

    suspend fun normalize(
        requested: List<RequestedTransformation>,
        originalVariantAttributes: Attributes,
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
        requested: RequestedTransformation,
        originalVariantAttributes: Attributes,
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
        requested: RequestedTransformation,
        originalAttributesDeferred: Deferred<Attributes>,
    ): Transformation {
        if (requested.originalVariant) {
            return Transformation.ORIGINAL_VARIANT
        }
        val (width, height) = normalizeDimensions(requested, originalAttributesDeferred)
        val (rotate, horizontalFlip) = normalizeRotateFlip(requested, originalAttributesDeferred)
        val format = normalizeFormat(requested, originalAttributesDeferred)
        return Transformation(
            width = width,
            height = height,
            canUpscale = requested.canUpscale,
            fit = requested.fit,
            gravity = requested.gravity,
            format = format,
            rotate = rotate,
            horizontalFlip = horizontalFlip,
            filter = requested.filter,
            blur = requested.blur ?: 0,
            quality = normalizeQuality(requested, format),
            padding =
                Padding(
                    amount = requested.pad ?: 0,
                    color = normalizeBackground(requested, format),
                ),
        ).also {
            // Cancel coroutine if we never used it and it's not in progress
            if (!originalAttributesDeferred.isActive && !originalAttributesDeferred.isCompleted) {
                originalAttributesDeferred.cancel()
            }
            logger.debug { "Normalized requested transformation: $requested to: $it" }
        }
    }

    private suspend fun normalizeDimensions(
        requested: RequestedTransformation,
        originalAttributesDeferred: Deferred<Attributes>,
    ): Pair<Int, Int> =
        when (requested.fit) {
            Fit.FIT -> {
                if ((requested.width == null && requested.height != null) || (requested.width != null && requested.height == null)) {
                    val originalVariant = originalAttributesDeferred.await()

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
                        originalAttributesDeferred.await().width,
                        originalAttributesDeferred.await().height,
                    )
                }
            }
            Fit.FILL, Fit.STRETCH, Fit.CROP -> {
                Pair(requireNotNull(requested.width), requireNotNull(requested.height))
            }
        }

    private suspend fun normalizeFormat(
        requested: RequestedTransformation,
        originalAttributesDeferred: Deferred<Attributes>,
    ): ImageFormat = requested.format ?: originalAttributesDeferred.await().format

    private suspend fun normalizeRotateFlip(
        requested: RequestedTransformation,
        originalAttributesDeferred: Deferred<Attributes>,
    ): Pair<Rotate, Boolean> =
        if (requested.rotate == Rotate.AUTO) {
            if (requested.flip != Flip.NONE) {
                throw IllegalArgumentException(
                    "Cannot specify flip (${ManipulationParameters.FLIP}) when r=${Rotate.AUTO.name.lowercase()}",
                )
            }
            ExifOrientations.fromExifOrientation(originalAttributesDeferred.await().orientation)
        } else {
            ExifOrientations.normalizeOrientation(requested.rotate, requested.flip)
        }

    private fun normalizeQuality(
        requested: RequestedTransformation,
        normalizedFormat: ImageFormat,
    ): Int {
        if (!normalizedFormat.vipsProperties.supportsQuality) {
            return normalizedFormat.vipsProperties.defaultQuality
        }

        return requested.quality ?: normalizedFormat.vipsProperties.defaultQuality
    }

    /**
     * Normalizes to a list of elements representing rgba or empty if no background at all.
     */
    private fun normalizeBackground(
        requested: RequestedTransformation,
        normalizedFormat: ImageFormat,
    ): List<Int> {
        if (requested.pad == null || requested.pad == 0) {
            // Background is useless unless padding is defined
            return emptyList()
        }
        if (requested.padColor == null) {
            return if (normalizedFormat.vipsProperties.supportsAlpha) ColorConverter.transparent else ColorConverter.white
        }

        return ColorConverter.toRgba(requested.padColor)
    }
}
