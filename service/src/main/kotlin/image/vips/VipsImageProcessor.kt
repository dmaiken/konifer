package image

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import asset.variant.AssetVariant
import image.model.ImageAttributes
import image.model.ImageFormat
import image.model.LQIPs
import image.model.PreProcessingProperties
import image.model.ProcessedImage
import image.model.RequestedImageAttributes
import io.asset.AssetStreamContainer
import io.image.ByteChannelOutputStream
import io.image.lqip.ImagePreviewGenerator
import io.image.model.Fit
import io.image.vips.VImageFactory
import io.image.vips.transformation.Resize
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.utils.io.ByteChannel
import io.path.configuration.PathConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.measureTime

class VipsImageProcessor(
    private val imagePreviewGenerator: ImagePreviewGenerator,
) {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    init {
        logger.info("Initializing Vips image processor")
    }

    /**
     * Preprocesses the image based on application configuration. Make sure to use the returned properties
     * since they reflect any changes performed on the image.
     */
    suspend fun preprocess(
        container: AssetStreamContainer,
        sourceFormat: ImageFormat,
        pathConfiguration: PathConfiguration,
        processedChannel: ByteChannel,
    ): ProcessedImage =
        withContext(Dispatchers.IO) {
            // Note: You cannot use coroutines in here unless we change up the way the arena is defined
            // FFM requires that only one thread access the native memory arena
            var attributes: ImageAttributes? = null
            val preProcessingProperties =
                pathConfiguration.imageProperties.preProcessing
            val resizedPreviewChannel = ByteChannel(autoFlush = true)
            try {
                Vips.run { arena ->
                    val sourceImage = VImageFactory.newFromContainer(arena, container)

                    val resized =
                        if (preProcessingProperties.enabled) {
                            Resize(
                                width = preProcessingProperties.maxWidth,
                                height = preProcessingProperties.maxHeight,
                                fit = preProcessingProperties.fit,
                                upscale = false,
                            ).transform(sourceImage)
                        } else {
                            sourceImage
                        }

                    val format =
                        if (preProcessingProperties.enabled) {
                            determineFormat(sourceFormat, preProcessingProperties)
                        } else {
                            sourceFormat
                        }
                    try {
                        if (pathConfiguration.imageProperties.previews.isNotEmpty()) {
                            generatePreviewVariant(
                                sourceImage = resized,
                                channel = resizedPreviewChannel,
                            )
                        }
                    } finally {
                        resizedPreviewChannel.close()
                    }
                    ByteChannelOutputStream(processedChannel).use {
                        resized.writeToStream(it, ".${format.extension}")
                    }
                    val fitAttribute =
                        if (resized.height >= sourceImage.height && resized.width >= sourceImage.width) {
                            // Crop was not used since the image was upscaled, so set the attribute to SCALE
                            Fit.SCALE
                        } else {
                            preProcessingProperties.fit
                        }
                    attributes =
                        ImageAttributes(
                            height = resized.height,
                            width = resized.width,
                            format = format,
                            fit = fitAttribute,
                        )
                }
                ProcessedImage(
                    attributes = attributes ?: throw IllegalStateException(),
                    lqip =
                        if (pathConfiguration.imageProperties.previews.isNotEmpty()) {
                            createImagePreviews(resizedPreviewChannel, pathConfiguration)
                        } else {
                            LQIPs.NONE
                        },
                )
            } finally {
                processedChannel.close()
            }
        }

    suspend fun generateVariant(
        source: AssetStreamContainer,
        pathConfiguration: PathConfiguration,
        requestedAttributes: RequestedImageAttributes,
        originalVariant: AssetVariant,
        outputChannel: ByteChannel,
    ): ProcessedImage =
        withContext(Dispatchers.IO) {
            var attributes: ImageAttributes? = null
            val resizedPreviewChannel = ByteChannel(autoFlush = true)
            var regenerateLqip = false
            try {
                Vips.run { arena ->
                    val image = VImageFactory.newFromContainer(arena, source)
                    // Determine if we need to downscale or upscale
                    val transformer =
                        Resize(
                            width = requestedAttributes.width,
                            height = requestedAttributes.height,
                            fit = requestedAttributes.fit,
                            upscale = true,
                        )
                    val resized = transformer.transform(image)
                    val requiresLqipRegeneration = transformer.requiresLqipRegeneration(image)
                    val newFormat = requestedAttributes.format ?: originalVariant.attributes.format
                    try {
                        if (requiresLqipRegeneration && pathConfiguration.imageProperties.previews.isNotEmpty()) {
                            regenerateLqip = true
                            generatePreviewVariant(
                                sourceImage = resized,
                                channel = resizedPreviewChannel,
                            )
                        }
                    } finally {
                        resizedPreviewChannel.close()
                    }

                    ByteChannelOutputStream(outputChannel).use {
                        resized.writeToStream(it, ".${newFormat.extension}")
                    }

                    attributes =
                        ImageAttributes(
                            width = resized.width,
                            height = resized.height,
                            format = newFormat,
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
                )
            } finally {
                outputChannel.close()
            }
        }

    private fun determineFormat(
        originalFormat: ImageFormat,
        preProcessingProperties: PreProcessingProperties,
    ): ImageFormat =
        if (preProcessingProperties.imageFormat != null) {
            if (preProcessingProperties.imageFormat != originalFormat) {
                logger.info("Converting image from $originalFormat to ${preProcessingProperties.imageFormat}")
            }
            preProcessingProperties.imageFormat
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
                previews = imagePreviewGenerator.generatePreviews(previewImageChannel, pathConfiguration)
            }
        logger.info("Created previews in: ${duration.inWholeMilliseconds}ms")

        return previews
    }

    private fun generatePreviewVariant(
        sourceImage: VImage,
        channel: ByteChannel,
    ) {
        val previewImage =
            Resize(
                width = 32,
                height = 32,
                fit = Fit.SCALE,
                upscale = false,
            ).transform(sourceImage)
        ByteChannelOutputStream(channel).use {
            previewImage.writeToStream(it, ".${ImageFormat.PNG.extension}")
        }
    }
}
