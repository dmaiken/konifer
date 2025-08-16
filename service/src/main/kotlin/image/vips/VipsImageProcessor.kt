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
import io.image.hash.ImagePreviewGenerator
import io.image.vips.VImageFactory
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.utils.io.ByteChannel
import io.path.configuration.PathConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min
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
        mimeType: String,
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
            if (!preProcessingProperties.enabled) {
                try {
                    Vips.run { arena ->
                        val sourceImage = VImageFactory.newFromContainer(arena, container)
                        if (pathConfiguration.imageProperties.previews.isNotEmpty()) {
                            generatePreviewVariant(sourceImage, resizedPreviewChannel)
                        }
                        ByteChannelOutputStream(processedChannel).use {
                            sourceImage.writeToStream(it, ".${ImageFormat.fromMimeType(mimeType).extension}")
                        }
                        attributes =
                            ImageAttributes(
                                height = sourceImage.height,
                                width = sourceImage.width,
                                mimeType = mimeType,
                            )
                    }
                } finally {
                    resizedPreviewChannel.close()
                }
                return@withContext ProcessedImage(
                    attributes = attributes ?: throw IllegalStateException(),
                    lqip =
                        if (pathConfiguration.imageProperties.previews.isNotEmpty()) {
                            createImagePreviews(resizedPreviewChannel, pathConfiguration)
                        } else {
                            LQIPs.NONE
                        },
                )
            }
            try {
                Vips.run { arena ->
                    val sourceImage = VImageFactory.newFromContainer(arena, container)
                    try {
                        if (pathConfiguration.imageProperties.previews.isNotEmpty()) {
                            generatePreviewVariant(sourceImage, resizedPreviewChannel)
                        }
                    } finally {
                        resizedPreviewChannel.close()
                    }

                    val resized =
                        scale(
                            image = sourceImage,
                            width = preProcessingProperties.maxWidth,
                            height = preProcessingProperties.maxHeight,
                        )

                    val newMimeType = determineMimeType(mimeType, preProcessingProperties)
                    ByteChannelOutputStream(processedChannel).use {
                        resized.writeToStream(it, ".${ImageFormat.fromMimeType(newMimeType).extension}")
                    }
                    attributes =
                        ImageAttributes(
                            height = resized.height,
                            width = resized.width,
                            mimeType = newMimeType,
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
        requestedAttributes: RequestedImageAttributes,
        originalVariant: AssetVariant,
        outputChannel: ByteChannel,
    ): ProcessedImage =
        withContext(Dispatchers.IO) {
            var attributes: ImageAttributes? = null
            try {
                Vips.run { arena ->
                    VImage.thumbnail()
                    val image = VImageFactory.newFromContainer(arena, source)
                    // Determine if we need to downscale or upscale
                    val resized =
                        scale(
                            image = image,
                            width = requestedAttributes.width,
                            height = requestedAttributes.height,
                        )
                    val mimeType = requestedAttributes.mimeType ?: originalVariant.attributes.mimeType
                    val imageFormat = ImageFormat.fromMimeType(mimeType)

                    ByteChannelOutputStream(outputChannel).use {
                        resized.writeToStream(it, ".${imageFormat.extension}")
                    }

                    attributes =
                        ImageAttributes(
                            width = resized.width,
                            height = resized.height,
                            mimeType = mimeType,
                        )
                }

                ProcessedImage(
                    attributes = attributes ?: throw IllegalStateException(),
                    // This will change when we start adding color filters
                    lqip = originalVariant.lqip,
                )
            } finally {
                outputChannel.close()
            }
        }

    /**
     * Scales the image to fit within the given width and height. Height or width may be smaller based on the
     * image's aspect ratio. If both width and height are null, the image is not downscaled.
     */
    private fun scale(
        image: VImage,
        width: Int?,
        height: Int?,
    ): VImage {
        if (width == null && height == null) {
            logger.info("Preprocessing width and height are not set, skipping preprocessing downscaling")
            return image
        }
        // Compute scale so that the image fits within max dimensions
        val widthRatio =
            width?.let {
                it.toDouble() / image.width
            } ?: 1.0
        val heightRatio =
            height?.let {
                it.toDouble() / image.height
            } ?: 1.0
        val scale = min(widthRatio, heightRatio)

        // Don't upscale
        if (scale >= 1.0) return image

        logger.info("Scaling image to $scale based on max width $width and max height $height")

        return image.resize(scale)
    }

    private fun thumbnail(
        image: VImage,
        width: Int,
        height: Int
    ): VImage {
        VImage.thumbnail()
    }

    private fun determineMimeType(
        originalMimeType: String,
        preProcessingProperties: PreProcessingProperties,
    ) = if (preProcessingProperties.imageFormat != null) {
        if (preProcessingProperties.imageFormat.mimeType != originalMimeType) {
            logger.info("Converting image from $originalMimeType to ${preProcessingProperties.imageFormat.mimeType}")
        }
        preProcessingProperties.imageFormat.mimeType
    } else {
        originalMimeType
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
        originalImage: VImage,
        channel: ByteChannel,
    ) {
        val previewImage =
            scale(
                image = originalImage,
                width = 32,
                height = 32,
            )
        ByteChannelOutputStream(channel).use {
            previewImage.writeToStream(it, ".${ImageFormat.PNG.extension}")
        }
    }
}
