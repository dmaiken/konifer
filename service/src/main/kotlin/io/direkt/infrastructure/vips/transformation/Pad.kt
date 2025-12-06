package io.direkt.infrastructure.vips.transformation

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.VipsOption
import app.photofox.vipsffm.enums.VipsExtend
import io.direkt.image.model.Transformation
import io.direkt.infrastructure.vips.VipsOptionNames.OPTION_BACKGROUND
import io.direkt.infrastructure.vips.VipsOptionNames.OPTION_EXTEND
import io.direkt.infrastructure.vips.pipeline.VipsTransformationResult
import io.ktor.util.logging.KtorSimpleLogger
import java.lang.foreign.Arena

/**
 * Pads an image with a specified background. Ignores the alpha band in the [Transformation.background] if
 * the [Transformation.format] does not support alpha.
 */
object Pad : VipsTransformer {
    private val alphaBand = listOf(255.0)

    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    override val name: String = "Pad"

    override fun requiresTransformation(
        arena: Arena,
        source: VImage,
        transformation: Transformation,
    ): Boolean = transformation.pad > 0 && transformation.background.isNotEmpty()

    override fun transform(
        arena: Arena,
        source: VImage,
        transformation: Transformation,
    ): VipsTransformationResult {
        if (transformation.background.size !in 3..4) {
            throw IllegalArgumentException("Illegal background definition: ${transformation.background}")
        }
        val pad = transformation.pad

        val preprocessedBackground = addOrRemoveAlphaIfNeeded(source, transformation)
        val preprocessedSource = addAlphaBandToImageIfNeeded(source, preprocessedBackground.size == 4, transformation.background)

        val processed =
            preprocessedSource.embed(
                pad,
                pad,
                (pad * 2) + preprocessedSource.width,
                (pad * 2) + preprocessedSource.height,
                VipsOption.Enum(OPTION_EXTEND, VipsExtend.EXTEND_BACKGROUND),
                VipsOption.ArrayDouble(OPTION_BACKGROUND, preprocessedBackground),
            )

        return VipsTransformationResult(processed, requiresLqipRegeneration = true)
    }

    private fun addOrRemoveAlphaIfNeeded(
        source: VImage,
        transformation: Transformation,
    ): List<Double> {
        val background = transformation.background
        if (!transformation.format.vipsProperties.supportsAlpha) {
            logger.info("Format ${transformation.format} does not support alpha, stripping alpha from background")
            return background.take(3).map { it.toDouble() }
        }
        return if (source.hasAlpha() && background.size == 3) {
            // Add alpha band to background
            logger.info("Source has an alpha band and background does not, adding opaque alpha band (255)")
            listOf(background[0], background[1], background[2], 255)
        } else {
            background
        }.map { it.toDouble() }
    }

    private fun addAlphaBandToImageIfNeeded(
        source: VImage,
        requiresAlpha: Boolean,
        background: List<Int>,
    ): VImage {
        if (requiresAlpha && source.hasAlpha()) {
            return source
        }
        if (requiresAlpha) {
            logger.info("Source does not have an alpha band but one is required for background: $background, adding opaque alpha band")
            return source.bandjoinConst(alphaBand)
        }
        return source
    }
}
