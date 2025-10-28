package io.image.vips

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import io.asset.AssetStreamContainer
import io.asset.handler.TransformationNormalizer
import io.asset.variant.AssetVariant
import io.image.ByteChannelOutputStream
import io.image.lqip.ImagePreviewGenerator
import io.image.model.Attributes
import io.image.model.Fit
import io.image.model.Gravity
import io.image.model.ImageFormat
import io.image.model.LQIPs
import io.image.model.PreProcessedImage
import io.image.model.PreProcessingProperties
import io.image.model.ProcessedImage
import io.image.model.Transformation
import io.image.vips.pipeline.VipsPipelines.lqipVariantPipeline
import io.image.vips.pipeline.VipsPipelines.preProcessingPipeline
import io.image.vips.pipeline.VipsPipelines.variantGenerationPipeline
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.utils.io.ByteChannel
import io.path.configuration.PathConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.lang.foreign.Arena
import kotlin.time.measureTime

class VipsImageProcessor(
    private val transformationNormalizer: TransformationNormalizer,
) {
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
        container: AssetStreamContainer,
        sourceFormat: ImageFormat,
        pathConfiguration: PathConfiguration,
        outputChannel: ByteChannel,
    ): PreProcessedImage =
        withContext(Dispatchers.IO) {
            val requestedTransformation = pathConfiguration.imageProperties.preProcessing.requestedImageTransformation
            // Note: You cannot use coroutines in here unless we change up the way the arena is defined
            // FFM requires that only one thread access the native memory arena
            var attributes: Attributes? = null
            val preProcessingProperties =
                pathConfiguration.imageProperties.preProcessing
            val resizedPreviewChannel = ByteChannel(autoFlush = true)
            try {
                Vips.run { arena ->
                    val sourceImage = VImageFactory.newFromContainer(arena, container)
                    // Need a better way to do this, but that requires not using
                    // Vips to get the image attributes
                    val transformation =
                        if (preProcessingProperties.enabled) {
                            runBlocking {
                                transformationNormalizer.normalize(
                                    requested = requestedTransformation,
                                    originalVariantAttributes =
                                        Attributes(
                                            width = sourceImage.width,
                                            height = sourceImage.height,
                                            format = sourceFormat,
                                        ),
                                )
                            }
                        } else {
                            null
                        }
                    val preProcessed = preProcessingPipeline.run(arena, sourceImage, transformation)

                    val format =
                        if (preProcessingProperties.enabled) {
                            determineFormat(sourceFormat, preProcessingProperties)
                        } else {
                            sourceFormat
                        }
                    try {
                        if (pathConfiguration.imageProperties.previews.isNotEmpty()) {
                            generatePreviewVariant(
                                arena = arena,
                                sourceImage = preProcessed.processed,
                                channel = resizedPreviewChannel,
                            )
                        }
                    } finally {
                        resizedPreviewChannel.close()
                    }
                    VipsEncoder.writeToStream(preProcessed.processed, format, transformation?.quality, outputChannel)

                    attributes =
                        Attributes(
                            width = preProcessed.processed.width,
                            height = preProcessed.processed.height,
                            format = format,
                        )
                }
                PreProcessedImage(
                    attributes = attributes ?: throw IllegalStateException(),
                    lqip =
                        if (pathConfiguration.imageProperties.previews.isNotEmpty()) {
                            createImagePreviews(resizedPreviewChannel, pathConfiguration)
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
        pathConfiguration: PathConfiguration,
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
                    val image = VImageFactory.newFromContainer(arena, source)
                    val variantResult = variantGenerationPipeline.run(arena, image, transformation)
                    try {
                        if (variantResult.requiresLqipRegeneration && pathConfiguration.imageProperties.previews.isNotEmpty()) {
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
                        Attributes(
                            width = variantResult.processed.width,
                            height = variantResult.processed.height,
                            format = transformation.format,
                        )
                }

                ProcessedImage(
                    attributes = attributes ?: throw IllegalStateException(),
                    // This will change when we start adding color filters
                    lqip =
                        if (regenerateLqip) {
                            createImagePreviews(resizedPreviewChannel, pathConfiguration)
                        } else {
                            originalVariant.lqip
                        },
                    transformation = transformation,
                )
            } finally {
                outputChannel.close()
            }
        }

    private fun determineFormat(
        originalFormat: ImageFormat,
        preProcessingProperties: PreProcessingProperties,
    ): ImageFormat =
        if (preProcessingProperties.format != null) {
            if (preProcessingProperties.format != originalFormat) {
                logger.info("Converting image from $originalFormat to ${preProcessingProperties.format}")
            }
            preProcessingProperties.format
        } else {
            originalFormat
        }

    private suspend fun createImagePreviews(
        previewImageChannel: ByteChannel,
        pathConfiguration: PathConfiguration,
    ): LQIPs {
        val previews: LQIPs
        val duration =
            measureTime {
                previews = ImagePreviewGenerator.generatePreviews(previewImageChannel, pathConfiguration)
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
            previewResult.processed.writeToStream(it, ".${ImageFormat.PNG.extension}")
        }
    }
}
