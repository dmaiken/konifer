package io.konifer.infrastructure.vips

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import app.photofox.vipsffm.VipsOption
import io.konifer.domain.image.Fit
import io.konifer.domain.image.Gravity
import io.konifer.domain.image.ImageFormat
import io.konifer.domain.image.LQIPImplementation
import io.konifer.domain.image.PreProcessedImage
import io.konifer.domain.ports.TransformationDataContainer
import io.konifer.domain.variant.Attributes
import io.konifer.domain.variant.LQIPs
import io.konifer.domain.variant.Transformation
import io.konifer.infrastructure.vips.pipeline.VipsPipelines.lqipVariantPipeline
import io.konifer.infrastructure.vips.pipeline.VipsPipelines.preProcessingPipeline
import io.konifer.infrastructure.vips.pipeline.VipsPipelines.variantGenerationPipeline
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.lang.foreign.Arena
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.pathString

class VipsImageProcessor {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    init {
        logger.info("Initializing Vips image processor")
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
            )
    }

    /**
     * Preprocesses the image based on application configuration. Make sure to use the returned properties
     * since they reflect any changes performed on the image.
     */
    suspend fun preprocess(
        source: Path,
        output: Path,
        sourceFormat: ImageFormat,
        lqipImplementations: Set<LQIPImplementation>,
        transformation: Transformation,
    ): PreProcessedImage =
        withContext(Dispatchers.IO) {
            // Note: You cannot use coroutines in here unless we change up the way the arena is defined
            // FFM requires that only one thread access the native memory arena
            var attributes: Attributes? = null
            val previewOutputStream = ByteArrayOutputStream()
            Vips.run { arena ->
                val decoderOptions =
                    createDecoderOptions(
                        sourceFormat = sourceFormat,
                        destinationFormat = transformation.format,
                    )
                val sourceImage = VImage.newFromFile(arena, source.toFile().absolutePath, *decoderOptions)
                val preProcessed = preProcessingPipeline.run(arena, sourceImage, transformation)

                if (lqipImplementations.isNotEmpty()) {
                    generatePreviewVariant(
                        arena = arena,
                        sourceImage = preProcessed.processed,
                        variantStream = previewOutputStream,
                    )
                }
                if (preProcessed.appliedTransformations.isNotEmpty() || sourceFormat != transformation.format) {
                    VipsEncoder.writeToFile(
                        source = preProcessed.processed,
                        file = output,
                        format = transformation.format,
                        quality = transformation.quality,
                    )
                } else {
                    // Encoding is where all the work is done - don't bother if the image was not transformed
                    logger.info("No applied transformations for image, not encoding image with vips")
                    Files.createSymbolicLink(output, source)
                }

                attributes =
                    AttributesFactory.createAttributes(
                        image = preProcessed.processed,
                        sourceFormat = sourceFormat,
                        destinationFormat = transformation.format,
                    )
            }
            PreProcessedImage(
                attributes = checkNotNull(attributes),
                lqip =
                    if (lqipImplementations.isNotEmpty()) {
                        ImagePreviewGenerator.generatePreviews(previewOutputStream.toByteArray(), lqipImplementations)
                    } else {
                        LQIPs.NONE
                    },
            )
        }

    suspend fun generateVariants(
        source: Path,
        transformationDataContainers: List<TransformationDataContainer>,
        lqipImplementations: Set<LQIPImplementation>,
    ) = withContext(Dispatchers.IO) {
        Vips.run { arena ->
            // Some transformations may want all pages, if present
            val sourceByDecoderOptions = mutableMapOf<Array<VipsOption>, VImage>()
            val sourceFormat = ImageFormat.fromExtension(".${source.extension}")
            for (container in transformationDataContainers) {
                val (transformation, output) = container
                val decoderOptions =
                    createDecoderOptions(
                        sourceFormat = sourceFormat,
                        destinationFormat = transformation.format,
                    )
                val image =
                    sourceByDecoderOptions
                        .getOrPut(decoderOptions) {
                            VImage.newFromFile(arena, source.pathString, *decoderOptions)
                        }.copy()

                val variantResult = variantGenerationPipeline.run(arena, image, transformation)

                if (variantResult.requiresLqipRegeneration && lqipImplementations.isNotEmpty()) {
                    val previewVariantStream = ByteArrayOutputStream()
                    generatePreviewVariant(
                        arena = arena,
                        sourceImage = variantResult.processed,
                        variantStream = previewVariantStream,
                    )
                    container.lqips =
                        ImagePreviewGenerator.generatePreviews(
                            source = previewVariantStream.toByteArray(),
                            lqipImplementations = lqipImplementations,
                        )
                }

                VipsEncoder.writeToFile(
                    source = variantResult.processed,
                    format = transformation.format,
                    quality = transformation.quality,
                    file = output,
                )

                container.attributes =
                    AttributesFactory.createAttributes(
                        image = variantResult.processed,
                        sourceFormat = sourceFormat,
                        destinationFormat = transformation.format,
                    )
            }
        }
    }

    private fun generatePreviewVariant(
        arena: Arena,
        sourceImage: VImage,
        variantStream: OutputStream,
    ) {
        val previewResult = lqipVariantPipeline.run(arena, sourceImage.copy(), lqipTransformation)
        previewResult.processed.writeToStream(variantStream, ImageFormat.PNG.extension)
    }
}
