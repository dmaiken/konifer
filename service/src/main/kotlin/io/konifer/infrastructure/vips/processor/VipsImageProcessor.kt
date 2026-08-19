package io.konifer.infrastructure.vips.processor

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import app.photofox.vipsffm.VipsOption
import io.konifer.common.image.Fit
import io.konifer.common.image.Gravity
import io.konifer.common.image.ImageFormat
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.image.LQIPImplementation
import io.konifer.domain.image.fromExtension
import io.konifer.domain.ports.TransformationDataContainer
import io.konifer.domain.variant.Attributes
import io.konifer.domain.variant.LQIPs
import io.konifer.domain.variant.Transformation
import io.konifer.infrastructure.vips.ImagePreviewGenerator
import io.konifer.infrastructure.vips.VipsEncoder
import io.konifer.infrastructure.vips.createDecoderOptions
import io.konifer.infrastructure.vips.decode.DecodedVipsImage
import io.konifer.infrastructure.vips.pipeline.VipsPipelines.lqipVariantPipeline
import io.konifer.infrastructure.vips.pipeline.VipsPipelines.preProcessingPipeline
import io.konifer.infrastructure.vips.pipeline.VipsPipelines.variantGenerationPipeline
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.lang.foreign.Arena
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.pathString

class VipsImageProcessor {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    init {
        // Not necessary since this will be a long-running service
        Vips.disableOperationCache()
    }

    companion object {
        val lqipTransformation =
            Transformation(
                width = 32,
                height = 32,
                format = ImageFormat.PNG,
                fit = Fit.FIT,
                gravity = Gravity.CENTER,
                colorSpace = ColorSpace.SRGB,
            )
    }

    /**
     * Preprocesses the image based on application configuration. Make sure to use the returned properties
     * since they reflect any changes performed on the image.
     */
    fun preprocess(
        arena: Arena,
        source: DecodedVipsImage,
        sourceFormat: ImageFormat,
        transformationDataContainer: TransformationDataContainer,
        lqipImplementations: Set<LQIPImplementation>,
    ): PreprocessOutput {
        // Note: You cannot use coroutines in here unless we change up the way the arena is defined
        // FFM requires that only one thread access the native memory arena
        val transformation = transformationDataContainer.transformation
        val preProcessed =
            preProcessingPipeline.run(
                arena = arena,
                source = source,
                transformation = transformation,
            )

        transformationDataContainer.attributes.complete(
            Attributes.createAttributes(
                image = preProcessed.processed,
                sourceFormat = sourceFormat,
                destinationFormat = transformation.format,
            ),
        )
        // we always want to generate lqips if configured when preprocessing even if the pipeline
        // says we don't need to
        if (lqipImplementations.isNotEmpty()) {
            generatePreviewVariant(
                arena = arena,
                sourceImage = preProcessed.processed,
                lqipImplementations = lqipImplementations,
                deferred = transformationDataContainer.lqips,
            )
        } else {
            transformationDataContainer.lqips.complete(null)
        }
        return if (preProcessed.appliedTransformations.isNotEmpty() || sourceFormat != transformation.format) {
            VipsEncoder.writeToStream(
                arena = arena,
                source = preProcessed.processed,
                format = transformation.format,
                quality = transformation.quality,
                outputChannel = transformationDataContainer.output,
            )
            PreprocessOutput.SourceTransformed
        } else {
            // Encoding is where all the work is done - don't bother if the image was not transformed
            logger.info("No applied transformations for image, bypassing libvips encoding")
            PreprocessOutput.SourceNotTransformed
        }
    }

    suspend fun generateVariants(
        sourceFile: Path,
        transformationDataContainers: List<TransformationDataContainer>,
        lqipImplementations: Set<LQIPImplementation>,
    ) = withContext(Dispatchers.IO) {
        Vips.run { arena ->
            // Some transformations may want all pages, if present
            val sourceByDecoderOptions = mutableMapOf<Array<VipsOption>, VImage>()
            val sourceFormat = ImageFormat.fromExtension(".${sourceFile.extension}")
            for ((transformation, output, lqips, attributes) in transformationDataContainers) {
                val decoderOptions =
                    createDecoderOptions(
                        sourceFormat = sourceFormat,
                        destinationFormat = transformation.format,
                    )
                val image =
                    sourceByDecoderOptions
                        .getOrPut(decoderOptions) {
                            VImage.newFromFile(arena, sourceFile.pathString, *decoderOptions)
                        }.copy()

                val source = DecodedVipsImage(image = image)
                val variantResult = variantGenerationPipeline.run(arena, source, transformation)

                if (variantResult.requiresLqipRegeneration && lqipImplementations.isNotEmpty()) {
                    generatePreviewVariant(
                        arena = arena,
                        sourceImage = variantResult.processed,
                        lqipImplementations = lqipImplementations,
                        deferred = lqips,
                    )
                } else {
                    lqips.complete(null)
                }
                attributes.complete(
                    Attributes.createAttributes(
                        image = variantResult.processed,
                        sourceFormat = sourceFormat,
                        destinationFormat = transformation.format,
                    ),
                )

                VipsEncoder.writeToStream(
                    arena = arena,
                    source = variantResult.processed,
                    format = transformation.format,
                    quality = transformation.quality,
                    outputChannel = output,
                )
            }
        }
    }

    private fun generatePreviewVariant(
        arena: Arena,
        sourceImage: VImage,
        lqipImplementations: Set<LQIPImplementation>,
        deferred: CompletableDeferred<LQIPs?>,
    ) {
        val previewVariantStream = ByteArrayOutputStream()
        val source =
            DecodedVipsImage(
                image = sourceImage,
            )
        val previewResult = lqipVariantPipeline.run(arena, source.copy(), lqipTransformation)
        previewResult.processed.writeToStream(previewVariantStream, ImageFormat.PNG.extension)

        deferred.complete(
            ImagePreviewGenerator.generatePreviews(
                source = previewVariantStream.toByteArray(),
                lqipImplementations = lqipImplementations,
            ),
        )
    }
}
