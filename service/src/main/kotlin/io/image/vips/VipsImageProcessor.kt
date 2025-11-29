package io.image.vips

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import app.photofox.vipsffm.VipsOption
import app.photofox.vipsffm.enums.VipsAccess
import io.asset.AssetStreamContainer
import io.asset.variant.AssetVariant
import io.image.AttributesFactory
import io.image.ByteChannelOutputStream
import io.image.lqip.ImagePreviewGenerator
import io.image.lqip.LQIPImplementation
import io.image.model.Attributes
import io.image.model.Fit
import io.image.model.Gravity
import io.image.model.ImageFormat
import io.image.model.LQIPs
import io.image.model.PreProcessedImage
import io.image.model.ProcessedImage
import io.image.model.Transformation
import io.image.vips.VipsOptionNames.OPTION_ACCESS
import io.image.vips.VipsOptionNames.OPTION_N
import io.image.vips.pipeline.VipsPipelines.lqipVariantPipeline
import io.image.vips.pipeline.VipsPipelines.preProcessingPipeline
import io.image.vips.pipeline.VipsPipelines.variantGenerationPipeline
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.utils.io.ByteChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        private val NO_OPTIONS = emptyArray<VipsOption>()
    }

    /**
     * Preprocesses the image based on application configuration. Make sure to use the returned properties
     * since they reflect any changes performed on the image.
     */
    suspend fun preprocess(
        container: AssetStreamContainer,
        sourceFormat: ImageFormat,
        lqipImplementations: Set<LQIPImplementation>,
        transformation: Transformation,
        outputChannel: ByteChannel,
    ): PreProcessedImage =
        withContext(Dispatchers.IO) {
            // Note: You cannot use coroutines in here unless we change up the way the arena is defined
            // FFM requires that only one thread access the native memory arena
            var attributes: Attributes? = null
            val resizedPreviewChannel = ByteChannel(autoFlush = true)
            // Safety
            if (!container.isDumpedToFile) {
                logger.info("Writing container to temporary file")
                container.toTemporaryFile()
            }
            try {
                Vips.run { arena ->
                    val decoderOptions =
                        createDecoderOptions(
                            sourceFormat = sourceFormat,
                            destinationFormat = transformation.format,
                        )
                    logger.info("Loading image from container")

                    val sourceImage = VImage.newFromFile(arena, container.getTemporaryFile().absolutePath, *decoderOptions)
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
                    VipsEncoder.writeToStream(preProcessed.processed, transformation.format, transformation.quality, outputChannel)

                    attributes =
                        AttributesFactory.createAttributes(
                            image = preProcessed.processed,
                            destinationFormat = transformation.format,
                        )
                }
                PreProcessedImage(
                    attributes = attributes ?: throw IllegalStateException(),
                    lqip =
                        if (lqipImplementations.isNotEmpty()) {
                            createImagePreviews(resizedPreviewChannel, lqipImplementations)
                        } else {
                            LQIPs.NONE
                        },
                )
            } finally {
                outputChannel.close()
            }
        }

    suspend fun generateVariant(
        source: AssetStreamContainer,
        lqipImplementations: Set<LQIPImplementation>,
        transformation: Transformation,
        originalVariant: AssetVariant,
        outputChannel: ByteChannel,
    ): ProcessedImage =
        withContext(Dispatchers.IO) {
            var attributes: Attributes? = null
            val resizedPreviewChannel = ByteChannel(autoFlush = true)
            var regenerateLqip = false
            try {
                Vips.run { arena ->
                    val decoderOptions =
                        createDecoderOptions(
                            sourceFormat = originalVariant.attributes.format,
                            destinationFormat = transformation.format,
                        )
                    val image = VImageFactory.newFromContainer(arena, source, decoderOptions)
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
                    VipsEncoder.writeToStream(variantResult.processed, transformation, outputChannel)

                    attributes =
                        AttributesFactory.createAttributes(
                            image = variantResult.processed,
                            destinationFormat = transformation.format,
                        )
                }

                ProcessedImage(
                    attributes = attributes ?: throw IllegalStateException(),
                    // This will change when we start adding color filters
                    lqip =
                        if (regenerateLqip) {
                            createImagePreviews(resizedPreviewChannel, lqipImplementations)
                        } else {
                            originalVariant.lqip
                        },
                    transformation = transformation,
                )
            } finally {
                outputChannel.close()
            }
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

    private fun createDecoderOptions(
        sourceFormat: ImageFormat,
        destinationFormat: ImageFormat,
    ): Array<VipsOption> {
        if (sourceFormat == ImageFormat.GIF && sourceFormat == destinationFormat) {
            return arrayOf(
                // Read all frames
                VipsOption.Int(OPTION_N, -1),
                // Sequential decoding
                VipsOption.Enum(OPTION_ACCESS, VipsAccess.ACCESS_SEQUENTIAL),
            )
        }
        if (sourceFormat == ImageFormat.GIF) {
            return arrayOf(
                // Read only first frame
                VipsOption.Int(OPTION_N, 1),
                // Sequential decoding
                VipsOption.Enum(OPTION_ACCESS, VipsAccess.ACCESS_SEQUENTIAL),
            )
        }
        return NO_OPTIONS
    }
}
