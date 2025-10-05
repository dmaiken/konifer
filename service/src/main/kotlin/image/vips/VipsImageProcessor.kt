package image

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import asset.variant.AssetVariant
import image.model.Attributes
import image.model.ImageFormat
import image.model.LQIPs
import image.model.PreProcessedImage
import image.model.PreProcessingProperties
import image.model.ProcessedImage
import image.model.Transformation
import io.asset.AssetStreamContainer
import io.asset.handler.RequestedTransformationNormalizer
import io.asset.variant.ImageVariantAttributes
import io.image.ByteChannelOutputStream
import io.image.lqip.ImagePreviewGenerator
import io.image.vips.VImageFactory
import io.image.vips.VipsPipelines.lqipVariantPipeline
import io.image.vips.transformation.ColorFilter
import io.image.vips.transformation.Resize
import io.image.vips.transformation.RotateFlip
import io.image.vips.vipsPipeline
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.utils.io.ByteChannel
import io.path.configuration.PathConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.lang.foreign.Arena
import kotlin.time.measureTime

class VipsImageProcessor(
    private val imagePreviewGenerator: ImagePreviewGenerator,
    private val requestedTransformationNormalizer: RequestedTransformationNormalizer,
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

                    val pipeline =
                        vipsPipeline {
                            checkIfLqipRegenerationNeeded = false
                            if (preProcessingProperties.enabled) {
                                // Need a better way to do this, but that requires not using
                                // Vips to get the image attributes
                                val transformation =
                                    runBlocking {
                                        requestedTransformationNormalizer.normalize(
                                            requested = requestedTransformation,
                                            originalVariantAttributes =
                                                ImageVariantAttributes(
                                                    width = sourceImage.width,
                                                    height = sourceImage.height,
                                                    format = sourceFormat,
                                                ),
                                        )
                                    }
                                add(
                                    Resize(
                                        width = transformation.width,
                                        height = transformation.height,
                                        fit = transformation.fit,
                                        upscale = false,
                                        gravity = transformation.gravity,
                                    ),
                                )
                                add(
                                    RotateFlip(
                                        rotate = transformation.rotate,
                                        horizontalFlip = transformation.horizontalFlip,
                                    ),
                                )
                                add(
                                    ColorFilter(
                                        filter = transformation.filter,
                                    ),
                                )
                            }
                        }.build()
                    val preProcessed = pipeline.run(arena, sourceImage)

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
                    ByteChannelOutputStream(processedChannel).use {
                        preProcessed.processed.writeToStream(it, ".${format.extension}")
                    }
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
                processedChannel.close()
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
                    val pipeline =
                        vipsPipeline {
                            add(
                                Resize(
                                    width = transformation.width,
                                    height = transformation.height,
                                    fit = transformation.fit,
                                    upscale = true,
                                    gravity = transformation.gravity,
                                ),
                            )
                            add(
                                RotateFlip(
                                    rotate = transformation.rotate,
                                    horizontalFlip = transformation.horizontalFlip,
                                ),
                            )
                            add(
                                ColorFilter(
                                    filter = transformation.filter,
                                ),
                            )
                        }.build()
                    val (variant, requiresLqipRegeneration) = pipeline.run(arena, image)
                    try {
                        if (requiresLqipRegeneration && pathConfiguration.imageProperties.previews.isNotEmpty()) {
                            regenerateLqip = true
                            generatePreviewVariant(
                                arena = arena,
                                sourceImage = variant,
                                channel = resizedPreviewChannel,
                            )
                        }
                    } finally {
                        resizedPreviewChannel.close()
                    }

                    ByteChannelOutputStream(outputChannel).use {
                        variant.writeToStream(it, ".${transformation.format.extension}")
                    }

                    attributes =
                        Attributes(
                            width = variant.width,
                            height = variant.height,
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
                previews = imagePreviewGenerator.generatePreviews(previewImageChannel, pathConfiguration)
            }
        logger.info("Created previews in: ${duration.inWholeMilliseconds}ms")

        return previews
    }

    private fun generatePreviewVariant(
        arena: Arena,
        sourceImage: VImage,
        channel: ByteChannel,
    ) {
        val (previewImage, _) = lqipVariantPipeline.run(arena, sourceImage)
        ByteChannelOutputStream(channel).use {
            previewImage.writeToStream(it, ".${ImageFormat.PNG.extension}")
        }
    }
}
