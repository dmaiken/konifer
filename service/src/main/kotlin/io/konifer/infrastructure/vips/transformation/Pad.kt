package io.konifer.infrastructure.vips.transformation

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.VipsOption
import app.photofox.vipsffm.enums.VipsExtend
import io.konifer.domain.variant.Transformation
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_BACKGROUND
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_EXTEND
import io.konifer.infrastructure.vips.pipeline.VipsTransformationResult
import io.ktor.util.logging.KtorSimpleLogger
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

        val preprocessedBackground = addOrRemoveAlphaIfNeeded(source, transformation)
        val preprocessedSource =
            addAlphaBandToImageIfNeeded(
                source = source,
                requiresAlpha = preprocessedBackground.size == 4,
                backgroundColor = color,
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
        if (!transformation.format.vipsProperties.supportsAlpha) {
            logger.info("Format ${transformation.format} does not support alpha, stripping alpha from background")
            return color.take(3).map { it.toDouble() }
        }
        return if (source.hasAlpha() && color.size == 3) {
            // Add alpha band to background
            logger.info("Source has an alpha band and background does not, adding opaque alpha band (255)")
            listOf(color[0], color[1], color[2], 255)
        } else {
            color
        }.map { it.toDouble() }
    }

    private fun addAlphaBandToImageIfNeeded(
        source: VImage,
        requiresAlpha: Boolean,
        backgroundColor: List<Int>,
    ): VImage {
        if (requiresAlpha && source.hasAlpha()) {
            return source
        }
        if (requiresAlpha) {
            logger.info("Source does not have an alpha band but one is required for color: $backgroundColor, adding opaque alpha band")
            return source.bandjoinConst(alphaBand)
        }
        return source
    }
}
