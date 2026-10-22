package io.konifer.domain.transformation

import io.konifer.common.image.Filter
import io.konifer.common.image.ImageFormat
import io.konifer.common.image.MetadataType
import io.konifer.common.image.TransformableColorSpace
import io.konifer.domain.image.vipsProperties
import io.konifer.domain.transformation.pixel.PixelTransformationNormalizer.normalizePixelTransformation
import io.konifer.domain.variant.TransformationLimitProperties
import io.konifer.domain.variant.attribute.Attributes
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.debug
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.math.min

class TransformationNormalizer {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    /**
     * Transform the supplied [RequestedTransformation] into a normalized [Transformation] where every transformation
     * attribute is specified, if needed, by deriving missing values using the original variant.
     * Every returned transformation satisfies the supplied [limits]; otherwise [InvalidTransformationException] is thrown.
     */
    suspend fun normalize(
        requested: List<RequestedTransformation>,
        originalVariantAttributes: Attributes,
        limits: TransformationLimitProperties,
    ): List<Transformation> =
        coroutineScope {
            if (requested.isEmpty()) {
                return@coroutineScope emptyList()
            }

            requested.map { request ->
                doNormalize(
                    requested = request,
                    limits = limits,
                    originalAttributesDeferred =
                        async {
                            originalVariantAttributes
                        },
                )
            }
        }

    /** Resolves source-dependent values and validates the result against [limits]. */
    suspend fun normalize(
        requested: RequestedTransformation,
        originalVariantAttributes: Attributes,
        limits: TransformationLimitProperties,
    ): Transformation =
        coroutineScope {
            doNormalize(
                requested = requested,
                limits = limits,
                originalAttributesDeferred =
                    async {
                        originalVariantAttributes
                    },
            )
        }

    private suspend fun doNormalize(
        requested: RequestedTransformation,
        originalAttributesDeferred: Deferred<Attributes>,
        limits: TransformationLimitProperties,
    ): Transformation {
        val (rotate, horizontalFlip, isAutoRotate) = RotateFlipNormalizer.normalizeRotateFlip(requested, originalAttributesDeferred)
        val (width, height) = TransformationDimensionNormalizer.normalizeDimensions(requested, rotate, originalAttributesDeferred)
        val normalizedFormat = normalizeFormat(requested, originalAttributesDeferred)
        val colorspace =
            ColorspaceNormalizer.normalizeColorSpace(
                requested = requested,
                normalizedFormat = normalizedFormat,
                originalAttributesDeferred = originalAttributesDeferred,
            )
        val isColorspaceLocked = requested.colorSpace != TransformableColorSpace.ORIGIN
        return Transformation(
            width = width,
            height = height,
            canUpscale = requested.canUpscale,
            fit = requested.fit,
            gravity = requested.gravity,
            format = normalizedFormat,
            rotate = rotate,
            horizontalFlip = horizontalFlip,
            filter = normalizeFilter(requested),
            blur = requested.blur ?: 0.toBlur(),
            quality = normalizeQuality(requested, normalizedFormat),
            padding =
                PaddingTransformation(
                    amount = requested.pad ?: 0.toPaddingAmount(),
                    color = normalizeBackground(requested, normalizedFormat),
                ),
            metadata = normalizeMetadata(requested),
            colorSpace = colorspace,
            isColorSpaceLocked = isColorspaceLocked,
            isAutoRotate = isAutoRotate,
            pixels =
                normalizePixelTransformation(
                    requested = requested,
                    normalizedColorSpace = colorspace,
                    normalizedFormat = normalizedFormat,
                    isColorspaceLocked = isColorspaceLocked,
                ),
        ).also {
            TransformationValidator.validateNormalizedTransformation(limits = limits, transformation = it)
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
}
