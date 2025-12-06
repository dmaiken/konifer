package io.direkt.infrastructure.vips

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import io.direkt.asset.model.AssetVariant
import io.direkt.domain.image.Fit
import io.direkt.domain.image.Gravity
import io.direkt.domain.image.ImageFormat
import io.direkt.domain.image.LQIPImplementation
import io.direkt.domain.image.PreProcessedImage
import io.direkt.domain.image.ProcessedImage
import io.direkt.domain.variant.Attributes
import io.direkt.domain.variant.LQIPs
import io.direkt.domain.variant.Transformation
import io.direkt.infrastructure.vips.pipeline.VipsPipelines.lqipVariantPipeline
import io.direkt.infrastructure.vips.pipeline.VipsPipelines.preProcessingPipeline
import io.direkt.infrastructure.vips.pipeline.VipsPipelines.variantGenerationPipeline
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.utils.io.ByteChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.foreign.Arena
import kotlin.time.measureTime

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
    ): PreProcessedImage =
        withContext(Dispatchers.IO) {
            // Note: You cannot use coroutines in here unless we change up the way the arena is defined
            // FFM requires that only one thread access the native memory arena
            var attributes: Attributes? = null
            val resizedPreviewChannel = ByteChannel(autoFlush = true)
            var processedFile: File? = null
            Vips.run { arena ->
                val decoderOptions =
                    createDecoderOptions(
                        sourceFormat = sourceFormat,
                        destinationFormat = transformation.format,
                    )
                logger.info("Loading image from container")

                val sourceImage = VImage.newFromFile(arena, source.absolutePath, *decoderOptions)
                val preProcessed = preProcessingPipeline.run(arena, sourceImage, transformation)
                try {
                    if (lqipImplementations.isNotEmpty()) {
                        generatePreviewVariant(
                            arena = arena,
                            sourceImage = preProcessed.processed,
                            channel = resizedPreviewChannel,
                        )
                    }
                } finally {
                    resizedPreviewChannel.close()
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
                        createImagePreviews(resizedPreviewChannel, lqipImplementations)
                    } else {
                        LQIPs.NONE
                    },
                result = checkNotNull(processedFile),
            )
        }

    suspend fun generateVariant(
        source: File,
        lqipImplementations: Set<LQIPImplementation>,
        transformation: Transformation,
        originalVariant: AssetVariant,
    ): ProcessedImage =
        withContext(Dispatchers.IO) {
            var attributes: Attributes? = null
            val resizedPreviewChannel = ByteChannel(autoFlush = true)
            var regenerateLqip = false
            var result: File? = null
            Vips.run { arena ->
                val decoderOptions =
                    createDecoderOptions(
                        sourceFormat = originalVariant.attributes.format,
                        destinationFormat = transformation.format,
                    )
                val image = VImage.newFromFile(arena, source.absolutePath, *decoderOptions)
                val variantResult = variantGenerationPipeline.run(arena, image, transformation)
                try {
                    if (variantResult.requiresLqipRegeneration && lqipImplementations.isNotEmpty()) {
                        regenerateLqip = true
                        generatePreviewVariant(
                            arena = arena,
                            sourceImage = variantResult.processed,
                            channel = resizedPreviewChannel,
                        )
                    }
                } finally {
                    resizedPreviewChannel.close()
                }
                result =
                    VipsEncoder.writeToFile(
                        source = variantResult.processed,
                        format = transformation.format,
                        quality = transformation.quality,
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
                        createImagePreviews(resizedPreviewChannel, lqipImplementations)
                    } else {
                        originalVariant.lqip
                    },
                transformation = transformation,
                result = checkNotNull(result),
            )
        }

    private suspend fun createImagePreviews(
        previewImageChannel: ByteChannel,
        lqipImplementations: Set<LQIPImplementation>,
    ): LQIPs {
        val previews: LQIPs
        val duration =
            measureTime {
                previews = ImagePreviewGenerator.generatePreviews(previewImageChannel, lqipImplementations)
            }
        logger.info("Created previews in: ${duration.inWholeMilliseconds}ms")

        return previews
    }

    private fun generatePreviewVariant(
        arena: Arena,
        sourceImage: VImage,
        channel: ByteChannel,
    ) {
        val previewResult = lqipVariantPipeline.run(arena, sourceImage.copy(), lqipTransformation)
        ByteChannelOutputStream(channel).use {
            previewResult.processed.writeToStream(it, ImageFormat.PNG.extension)
        }
    }
}
