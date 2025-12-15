package io.direkt.infrastructure.vips

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import app.photofox.vipsffm.VipsOption
import io.direkt.domain.image.Fit
import io.direkt.domain.image.Gravity
import io.direkt.domain.image.ImageFormat
import io.direkt.domain.image.LQIPImplementation
import io.direkt.domain.image.PreProcessedImage
import io.direkt.domain.image.ProcessedImage
import io.direkt.domain.ports.TransformationDataContainer
import io.direkt.domain.variant.Attributes
import io.direkt.domain.variant.LQIPs
import io.direkt.domain.variant.Transformation
import io.direkt.domain.variant.VariantData
import io.direkt.infrastructure.vips.pipeline.VipsPipelines.lqipVariantPipeline
import io.direkt.infrastructure.vips.pipeline.VipsPipelines.preProcessingPipeline
import io.direkt.infrastructure.vips.pipeline.VipsPipelines.variantGenerationPipeline
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.lang.foreign.Arena

class VipsImageProcessor {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    init {
        logger.info("Initializing Vips image processor")
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
        source: File,
        sourceFormat: ImageFormat,
        lqipImplementations: Set<LQIPImplementation>,
        transformation: Transformation,
        output: File,
    ): PreProcessedImage =
        withContext(Dispatchers.IO) {
            // Note: You cannot use coroutines in here unless we change up the way the arena is defined
            // FFM requires that only one thread access the native memory arena
            var attributes: Attributes? = null
            var processedFile: File? = null
            val previewOutputStream = ByteArrayOutputStream()
            Vips.run { arena ->
                val decoderOptions =
                    createDecoderOptions(
                        sourceFormat = sourceFormat,
                        destinationFormat = transformation.format,
                    )
                val sourceImage = VImage.newFromFile(arena, source.absolutePath, *decoderOptions)
                val preProcessed = preProcessingPipeline.run(arena, sourceImage, transformation)

                if (lqipImplementations.isNotEmpty()) {
                    generatePreviewVariant(
                        arena = arena,
                        sourceImage = preProcessed.processed,
                        variantStream = previewOutputStream,
                    )
                }
                processedFile =
                    if (preProcessed.appliedTransformations.isNotEmpty() || sourceFormat != transformation.format) {
                        VipsEncoder.writeToFile(preProcessed.processed, transformation.format, transformation.quality)
                    } else {
                        // Encoding is where all the work is done - don't bother if the image was not transformed
                        logger.info("No applied transformations for image, not encoding image with vips")
                        source
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
                result = checkNotNull(processedFile),
            )
        }

    suspend fun generateVariants(
        source: File,
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
                val image = sourceByDecoderOptions.getOrPut(decoderOptions) {
                    VImage.newFromFile(arena, source.absolutePath, *decoderOptions)
                }.copy()

                val variantResult = variantGenerationPipeline.run(arena, image, transformation)

                if (variantResult.requiresLqipRegeneration && lqipImplementations.isNotEmpty()) {
                    val previewVariantStream = ByteArrayOutputStream()
                    generatePreviewVariant(
                        arena = arena,
                        sourceImage = variantResult.processed,
                        variantStream = previewVariantStream,
                    )
                    container.lqips = ImagePreviewGenerator.generatePreviews(
                        source = previewVariantStream.toByteArray(),
                        lqipImplementations = lqipImplementations
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

    suspend fun generateVariant(
        source: File,
        lqipImplementations: Set<LQIPImplementation>,
        transformation: Transformation,
        originalVariant: VariantData,
        output: File,
    ): ProcessedImage =
        withContext(Dispatchers.IO) {
            var attributes: Attributes? = null
            val previewOutputStream = ByteArrayOutputStream()
            var regenerateLqip = false
            Vips.run { arena ->
                val decoderOptions =
                    createDecoderOptions(
                        sourceFormat = originalVariant.attributes.format,
                        destinationFormat = transformation.format,
                    )
                val image = VImage.newFromFile(arena, source.absolutePath, *decoderOptions)
                val variantResult = variantGenerationPipeline.run(arena, image, transformation)
                if (variantResult.requiresLqipRegeneration && lqipImplementations.isNotEmpty()) {
                    regenerateLqip = true
                    generatePreviewVariant(
                        arena = arena,
                        sourceImage = variantResult.processed,
                        variantStream = previewOutputStream,
                    )
                }
                VipsEncoder.writeToFile(
                    source = variantResult.processed,
                    format = transformation.format,
                    quality = transformation.quality,
                    file = output,
                )

                attributes =
                    AttributesFactory.createAttributes(
                        image = variantResult.processed,
                        sourceFormat = originalVariant.attributes.format,
                        destinationFormat = transformation.format,
                    )
            }

            ProcessedImage(
                attributes = checkNotNull(attributes),
                // This will change when we start adding color filters
                lqip =
                    if (regenerateLqip) {
                        ImagePreviewGenerator.generatePreviews(previewOutputStream.toByteArray(), lqipImplementations)
                    } else {
                        originalVariant.lqips
                    },
                transformation = transformation,
            )
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
