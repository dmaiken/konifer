package io.konifer.infrastructure.vips.transformer

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.VipsOption
import app.photofox.vipsffm.enums.VipsExtend
import app.photofox.vipsffm.enums.VipsInterpretation
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.image.vipsProperties
import io.konifer.domain.variant.Transformation
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_BACKGROUND
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_BANDS
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_EXTEND
import io.konifer.infrastructure.vips.pipeline.AppliedTransformation
import io.konifer.infrastructure.vips.pipeline.VipsTransformationResult
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.debug
import java.lang.foreign.Arena

/**
 * Pads an image with a specified background. Ignores the alpha band in the [Transformation.padding] if
 * the [Transformation.format] does not support alpha.
 */
object Pad : VipsTransformer {
    private val alphaBand = listOf(255.0)

    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    override val name: String = "Pad"
    override val requiresAlphaState: AlphaState = AlphaState.UN_PREMULTIPLIED

    override fun requiresTransformation(
        arena: Arena,
        source: VImage,
        transformation: Transformation,
        appliedTransformations: List<AppliedTransformation>,
    ): Boolean = transformation.padding.amount > 0 && transformation.padding.color.isNotEmpty()

    override fun transform(
        arena: Arena,
        source: VImage,
        transformation: Transformation,
    ): VipsTransformationResult {
        val (amount, color) = transformation.padding
        if (color.size !in 3..4) {
            throw IllegalArgumentException("Illegal background definition: ${transformation.padding.color}")
        }

        // Check canvas state and padding intent
        val bands = source.getInt(OPTION_BANDS)
        val isGrayscaleCanvas = bands == 1 || (bands == 2 && source.hasAlpha())
        val isColorfulPadding = color[0] != color[1] || color[1] != color[2]

        val isGrayscaleRequested = transformation.colorSpace == ColorSpace.Grayscale && transformation.isColorSpaceLocked

        // Only inflate to 3-band if they asked for color padding AND didn't explicitly lock the space to Grayscale
        val workingSource =
            if (isGrayscaleCanvas && isColorfulPadding && !isGrayscaleRequested) {
                logger.debug { "Grayscale canvas but colorful padding requested. Re-inflating canvas to sRGB." }
                source.colourspace(VipsInterpretation.INTERPRETATION_sRGB)
            } else {
                source
            }

        val preprocessedBackground = addOrRemoveAlphaIfNeeded(workingSource, transformation)
        val preprocessedSource =
            addAlphaBandToImageIfNeeded(
                source = workingSource,
                requiresAlpha = preprocessedBackground.size == 4,
            )

        val processed =
            preprocessedSource.embed(
                amount,
                amount,
                (amount * 2) + preprocessedSource.width,
                (amount * 2) + preprocessedSource.height,
                VipsOption.Enum(OPTION_EXTEND, VipsExtend.EXTEND_BACKGROUND),
                VipsOption.ArrayDouble(OPTION_BACKGROUND, preprocessedBackground),
            )

        return VipsTransformationResult(
            processed = processed,
            requiresLqipRegeneration = true,
        )
    }

    private fun addOrRemoveAlphaIfNeeded(
        source: VImage,
        transformation: Transformation,
    ): List<Double> {
        val color = transformation.padding.color
        val formatSupportsAlpha = transformation.format.vipsProperties.supportsAlpha
        val bands = source.getInt(OPTION_BANDS)

        // Determine Base Color (Luminance vs RGB)
        // Grayscale images are 1 band (or 2 if they already have alpha)
        val isGrayscale = bands == 1 || (bands == 2 && source.hasAlpha())

        val baseColor =
            if (isGrayscale) {
                logger.debug { "Source is grayscale. Crushing RGB padding color to luminance." }
                listOf(calculateLuminance(color))
            } else {
                color.take(3).map { it.toDouble() }
            }

        if (!formatSupportsAlpha) {
            logger.debug { "Format ${transformation.format} does not support alpha, stripping alpha from background" }
            return baseColor // Returns 1 or 3 bands
        }

        // The image needs an alpha channel if it already has one,
        // OR if the user explicitly requested a transparent padding color.
        val requestedAlpha = if (color.size == 4) color[3].toDouble() else 255.0
        val needsAlpha = source.hasAlpha() || requestedAlpha < 255.0

        return if (needsAlpha) {
            baseColor + requestedAlpha // Returns 2 or 4 bands
        } else {
            baseColor // Returns 1 or 3 bands
        }
    }

    private fun addAlphaBandToImageIfNeeded(
        source: VImage,
        requiresAlpha: Boolean,
    ): VImage {
        if (requiresAlpha && source.hasAlpha()) {
            return source
        }
        if (requiresAlpha) {
            logger.debug { "Source lacks alpha but padding requires it. Adding opaque alpha band." }
            return source.bandjoinConst(alphaBand)
        }
        return source
    }

    private fun calculateLuminance(color: List<Int>): Double {
        // Standard Rec. 709 luminance matching the Grayscale matrix
        return (color[0] * 0.2126) + (color[1] * 0.7152) + (color[2] * 0.0722)
    }
}
