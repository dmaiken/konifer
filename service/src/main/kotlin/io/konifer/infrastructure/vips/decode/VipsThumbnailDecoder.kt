package io.konifer.infrastructure.vips.decode

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.VipsOption
import app.photofox.vipsffm.enums.VipsSize
import io.konifer.common.image.Fit
import io.konifer.common.image.ImageFormat
import io.konifer.common.image.Rotate
import io.konifer.domain.transformation.Transformation
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_CROP
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_HEIGHT
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_NO_ROTATE
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_N_PAGES
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_SIZE
import io.konifer.infrastructure.vips.pipeline.AppliedTransformation
import io.konifer.infrastructure.vips.toVipsInteresting
import io.konifer.infrastructure.vips.transformer.AutoRotate
import io.konifer.infrastructure.vips.transformer.PixelAccess
import io.konifer.infrastructure.vips.transformer.Resize
import io.konifer.infrastructure.vips.transformer.ResizePlan
import java.lang.foreign.Arena
import java.nio.file.Path
import kotlin.io.path.absolutePathString

object VipsThumbnailDecoder {
    fun decode(
        arena: Arena,
        transformation: Transformation,
        sourceFormat: ImageFormat,
        sourceFile: Path,
    ): DecodedVipsImage {
        val normalVImage =
            VipsFileDecoder.decodeSource(
                arena = arena,
                destinationFormat = transformation.format,
                sourceFormat = sourceFormat,
                source = sourceFile,
            )
        val resizePlanningImage =
            if (transformation.isAutoRotate) normalVImage.autorot() else normalVImage
        val resizePlan = Resize.createPlan(resizePlanningImage, transformation)
        val shouldDecodeWithThumbnail =
            shouldDecodeWithThumbnail(
                decoded = normalVImage,
                transformation = transformation,
                resizePlan = resizePlan,
            )
        if (shouldDecodeWithThumbnail) {
            return decodeWithThumbnail(
                arena = arena,
                transformation = transformation,
                source = sourceFile,
                resizePlan = resizePlan,
            )
        }

        return DecodedVipsImage(
            image = normalVImage,
        )
    }

    private fun canDecodeWithThumbnail(
        decoded: VImage,
        transformation: Transformation,
    ): Boolean {
        val pageCount = decoded.getInt(OPTION_N_PAGES) ?: 1

        return pageCount == 1 &&
            transformation.fit != Fit.CROP &&
            (
                transformation.isAutoRotate ||
                    (transformation.rotate == Rotate.ZERO && !transformation.horizontalFlip)
            )
    }

    /**
     * Does a resize operation even need to be done here?
     */
    private fun shouldDecodeWithThumbnail(
        decoded: VImage,
        transformation: Transformation,
        resizePlan: ResizePlan,
    ): Boolean =
        canDecodeWithThumbnail(decoded, transformation) &&
            resizePlan.requiresTransformation

    private fun decodeWithThumbnail(
        arena: Arena,
        source: Path,
        transformation: Transformation,
        resizePlan: ResizePlan,
    ): DecodedVipsImage {
        val options =
            buildList {
                add(VipsOption.Int(OPTION_HEIGHT, resizePlan.height))
                add(sizeOption(transformation))
                cropOption(transformation)?.let(::add)
                add(VipsOption.Boolean(OPTION_NO_ROTATE, !transformation.isAutoRotate))
            }.toTypedArray()

        val image = VImage.thumbnail(arena, source.thumbnailFilename(), resizePlan.width, *options)

        return DecodedVipsImage(
            image = image,
            appliedTransformations = thumbnailAppliedTransformations(transformation),
            requiresLqipRegeneration =
                resizePlan.requiresLqipRegeneration ||
                    AutoRotate.changesImageOrientation(transformation),
        )
    }

    private fun thumbnailAppliedTransformations(transformation: Transformation): List<AppliedTransformation> =
        buildList {
            add(
                AppliedTransformation(
                    name = Resize.name,
                    exceptionMessage = null,
                ),
            )
            if (AutoRotate.changesImageOrientation(transformation)) {
                add(
                    AppliedTransformation(
                        name = AutoRotate.name,
                        exceptionMessage = null,
                    ),
                )
            }
        }

    private fun Path.thumbnailFilename(): String = "${absolutePathString()}[access=sequential]"

    private fun sizeOption(transformation: Transformation): VipsOption =
        when {
            transformation.fit == Fit.STRETCH -> {
                VipsOption.Enum(OPTION_SIZE, VipsSize.SIZE_FORCE)
            }
            !transformation.canUpscale -> {
                VipsOption.Enum(OPTION_SIZE, VipsSize.SIZE_DOWN)
            }
            else -> VipsOption.Enum(OPTION_SIZE, VipsSize.SIZE_BOTH)
        }

    private fun cropOption(transformation: Transformation): VipsOption? =
        when (transformation.fit) {
            Fit.FILL -> VipsOption.Enum(OPTION_CROP, transformation.gravity.toVipsInteresting())
            else -> null
        }
}

data class DecodedVipsImage(
    val image: VImage,
    val appliedTransformations: List<AppliedTransformation> = emptyList(),
    val requiresLqipRegeneration: Boolean = false,
    val pixelAccess: PixelAccess = PixelAccess.SEQUENTIAL,
) {
    fun copy(): DecodedVipsImage = copy(image = image.copy())
}
