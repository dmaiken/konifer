package io.image.vips.transformation

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.VipsOption
import app.photofox.vipsffm.enums.VipsExtend
import image.model.ImageFormat
import io.image.vips.VipsOption.VIPS_BACKGROUND
import io.image.vips.VipsOption.VIPS_EXTEND
import io.ktor.util.logging.KtorSimpleLogger
import java.lang.foreign.Arena

/**
 * Pads an image with a specified background. Ignores the alpha band in the [background] if the [format] does
 * not support alpha.
 */
class Pad(
    private val pad: Int,
    private val background: List<Int>,
    private val format: ImageFormat,
) : VipsTransformer {
    companion object {
        private val alphaBand = listOf(255.0)
    }

    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    override fun transform(
        arena: Arena,
        source: VImage,
    ): VImage {
        if (pad == 0 || background.isEmpty()) {
            return source
        }

        if (background.size !in 3..4) {
            throw IllegalArgumentException("Illegal background definition: $background")
        }

        val preprocessedBackground = addOrRemoveAlphaIfNeeded(source)
        val preprocessedSource = addAlphaBandToImageIfNeeded(source, preprocessedBackground.size == 4)

        return preprocessedSource.embed(
            pad,
            pad,
            (pad * 2) + preprocessedSource.width,
            (pad * 2) + preprocessedSource.height,
            VipsOption.Enum(VIPS_EXTEND, VipsExtend.EXTEND_BACKGROUND),
            VipsOption.ArrayDouble(VIPS_BACKGROUND, preprocessedBackground),
        )
    }

    override fun requiresLqipRegeneration(source: VImage) = pad > 0 && background.isNotEmpty()

    private fun addOrRemoveAlphaIfNeeded(source: VImage): List<Double> {
        if (!format.vipsProperties.supportsAlpha) {
            logger.info("Format $format does not support alpha, stripping alpha from background")
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
