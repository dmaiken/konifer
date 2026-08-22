package io.konifer.domain.transformation

import io.konifer.common.image.Filter
import io.konifer.common.image.ImageFormat
import io.konifer.common.image.MetadataType
import io.konifer.common.image.TransformableColorSpace
import io.konifer.domain.context.RequestedTransformation
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.image.vipsProperties
import io.konifer.domain.ports.AssetRepository
import io.konifer.domain.variant.Attributes
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.debug
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.math.min

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
        val (rotate, horizontalFlip, isAutoRotate) = RotateFlipNormalizer.normalizeRotateFlip(requested, originalAttributesDeferred)
        val (width, height) = TransformationDimensionNormalizer.normalizeDimensions(requested, rotate, originalAttributesDeferred)
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
            filter = normalizeFilter(requested),
            blur = requested.blur ?: 0.toBlur(),
            quality = normalizeQuality(requested, format),
            padding =
                PaddingTransformation(
                    amount = requested.pad ?: 0.toPaddingAmount(),
                    color = normalizeBackground(requested, format),
                ),
            metadata = normalizeMetadata(requested),
            colorSpace = normalizeColorSpace(requested, originalAttributesDeferred),
            isColorSpaceLocked = requested.colorSpace != TransformableColorSpace.ORIGIN,
            isAutoRotate = isAutoRotate,
        ).also {
            // Cancel coroutine if we never used it and it's not in progress
            if (!originalAttributesDeferred.isActive && !originalAttributesDeferred.isCompleted) {
                originalAttributesDeferred.cancel()
            }
            logger.debug { "Normalized requested transformation: $requested to: $it" }
        }
    }

    private suspend fun normalizeFormat(
        requested: RequestedTransformation,
        originalAttributesDeferred: Deferred<Attributes>,
    ): ImageFormat = requested.format ?: originalAttributesDeferred.await().format

    private fun normalizeQuality(
        requested: RequestedTransformation,
        normalizedFormat: ImageFormat,
    ): Quality {
        if (!normalizedFormat.vipsProperties.supportsQuality) {
            return normalizedFormat.vipsProperties.defaultQuality.toQuality()
        }

        return min(
            requested.quality?.value ?: normalizedFormat.vipsProperties.defaultQuality,
            normalizedFormat.vipsProperties.maxQuality,
        ).toQuality()
    }

    fun normalizeFilter(requested: RequestedTransformation): Filter {
        if ((requested.filter == Filter.GRAYSCALE || requested.filter == Filter.SEPIA) &&
            requested.colorSpace == TransformableColorSpace.GRAYSCALE
        ) {
            // Skip the filter since the color space will make this filter useless
            return Filter.NONE
        }

        return requested.filter
    }

    /**
     * Normalizes to a list of elements representing rgba or empty if no background at all.
     */
    private fun normalizeBackground(
        requested: RequestedTransformation,
        normalizedFormat: ImageFormat,
    ): List<Int> {
        if (requested.pad == null || requested.pad.value == 0) {
            // Background is useless unless padding is defined
            return emptyList()
        }
        if (requested.padColor == null) {
            return if (normalizedFormat.vipsProperties.supportsAlpha) ColorConverter.transparent else ColorConverter.white
        }

        return ColorConverter.toRgba(requested.padColor)
    }

    private fun normalizeMetadata(requested: RequestedTransformation): MetadataTransformation {
        val parsed =
            requested.stripMetadata
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?.map { MetadataType.valueOf(it.trim().uppercase()) }
                ?.toSet()
                ?: emptySet()

        return MetadataTransformation(
            strip = parsed,
        )
    }

    private suspend fun normalizeColorSpace(
        requested: RequestedTransformation,
        originalAttributesDeferred: Deferred<Attributes>,
    ): ColorSpace =
        when (requested.colorSpace) {
            TransformableColorSpace.ORIGIN -> originalAttributesDeferred.await().colorSpace
            TransformableColorSpace.P3 -> ColorSpace.P3
            TransformableColorSpace.SRGB -> ColorSpace.SRGB
            TransformableColorSpace.GRAYSCALE -> ColorSpace.Grayscale
        }
}
