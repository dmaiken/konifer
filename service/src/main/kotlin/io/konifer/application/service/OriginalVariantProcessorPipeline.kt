package io.konifer.application.service

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import io.konifer.common.image.ImageFormat
import io.konifer.domain.asset.AssetDataContainer
import io.konifer.domain.context.StoreRequestContext
import io.konifer.domain.ports.ContentProcessorResult
import io.konifer.domain.ports.OriginalVariantContentProcessor
import io.konifer.domain.ports.TransformationDataContainer
import io.konifer.domain.transformation.TransformationNormalizer
import io.konifer.domain.variant.Attributes
import io.konifer.domain.variant.LQIPs
import io.konifer.domain.variant.ProcessingPipeline
import io.konifer.domain.variant.Transformation
import io.konifer.infrastructure.TemporaryFileFactory
import io.konifer.infrastructure.teeStream
import io.konifer.infrastructure.vips.createDecoderOptions
import io.ktor.util.cio.readChannel
import io.ktor.util.cio.writeChannel
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.close
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.io.path.createLinkPointingTo
import kotlin.io.path.pathString

class OriginalVariantProcessorPipeline(
    private val transformationNormalizer: TransformationNormalizer,
    private val originalVariantContentProcessor: OriginalVariantContentProcessor,
) {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    suspend fun process(
        scope: CoroutineScope,
        container: AssetDataContainer,
        context: StoreRequestContext,
        format: ImageFormat,
    ): ProcessingPipeline {
        container.toTemporaryFile(format.extension)
        val hasEagerVariants =
            context.pathConfiguration.transform.eagerVariants
                .isNotEmpty()

        return if (context.requiresPreProcessing()) {
            buildPreProcessingPipeline(
                scope = scope,
                container = container,
                context = context,
                format = format,
                hasEagerVariants = hasEagerVariants,
            )
        } else {
            buildPassthroughPipeline(
                scope = scope,
                container = container,
                format = format,
                hasEagerVariants = hasEagerVariants,
            )
        }
    }

    private suspend fun buildPreProcessingPipeline(
        scope: CoroutineScope,
        container: AssetDataContainer,
        context: StoreRequestContext,
        format: ImageFormat,
        hasEagerVariants: Boolean,
    ): ProcessingPipeline {
        val transformation =
            normalizePreProcessing(
                context = context,
                container = container,
                sourceFormat = format,
            )

        val objectStoreChannel = ByteChannel()
        val outputChannel = ByteChannel()

        val transformationData =
            TransformationDataContainer(
                transformation = transformation,
                output = outputChannel,
            )

        val eagerVariantTemporaryFile =
            if (hasEagerVariants) {
                TemporaryFileFactory.createPreProcessedTempFile(format.extension)
            } else {
                null
            }
        val eagerVariantWriteChannel = eagerVariantTemporaryFile?.toFile()?.writeChannel()

        val schedulerProcess =
            scope.async {
                launch {
                    teeStream(
                        source = outputChannel,
                        firstChannel = objectStoreChannel,
                        secondChannel = eagerVariantWriteChannel,
                    )
                }

                val jobDeferred =
                    originalVariantContentProcessor.process(
                        sourceFormat = format,
                        lqipImplementations = context.pathConfiguration.image.previews,
                        source = container.getTemporaryFile(),
                        transformationDataContainer = transformationData,
                        uploadRuleset = context.pathConfiguration.uploadRuleset,
                    )

                jobDeferred.await()
            }

        return ProcessingPipeline(
            attributes = transformationData.attributes,
            outputChannel = objectStoreChannel,
            eagerVariantFile = eagerVariantTemporaryFile,
            processDeferred = schedulerProcess,
            lqips = transformationData.lqips,
        )
    }

    private suspend fun buildPassthroughPipeline(
        scope: CoroutineScope,
        container: AssetDataContainer,
        format: ImageFormat,
        hasEagerVariants: Boolean,
    ): ProcessingPipeline {
        val objectStoreChannel = ByteChannel()
        val eagerVariantFile =
            if (hasEagerVariants) {
                TemporaryFileFactory.createPreProcessedTempFile(format.extension).apply {
                    // Hard reference - not a symbolic link!! This prevents the underlying file
                    // from being deleted when the container is closed
                    createLinkPointingTo(container.getTemporaryFile())
                }
            } else {
                null
            }

        // Since there is no preprocessing required here, we just stream the original file back to the caller
        val passthroughProcess: Deferred<ContentProcessorResult> =
            scope.async {
                runCatching {
                    teeStream(
                        source = container.getTemporaryFile().readChannel(),
                        firstChannel = objectStoreChannel,
                        secondChannel = null,
                    )
                    ContentProcessorResult.Success
                }.onFailure { e ->
                    objectStoreChannel.close(e)
                }.getOrThrow()
            }

        return ProcessingPipeline(
            attributes =
                CompletableDeferred(
                    withContext(Dispatchers.IO) {
                        Attributes.createAttributes(
                            path = container.getTemporaryFile(),
                            format = format,
                        )
                    },
                ),
            outputChannel = objectStoreChannel,
            eagerVariantFile = eagerVariantFile,
            processDeferred = passthroughProcess,
            lqips = CompletableDeferred(LQIPs.NONE),
        )
    }

    private suspend fun normalizePreProcessing(
        context: StoreRequestContext,
        container: AssetDataContainer,
        sourceFormat: ImageFormat,
    ): Transformation =
        withContext(Dispatchers.IO) {
            val requestedTransformation =
                context.pathConfiguration.transform.preProcessing.image.requestedImageTransformation
            var transformation: Transformation? = null

            Vips.run { arena ->
                val destinationFormat =
                    context.pathConfiguration.transform.preProcessing.image.format
                        ?: sourceFormat
                // Even if this image is paged, just need to load one frame to get height/width
                // So don't specify "n" as an option
                val image =
                    VImage.newFromFile(
                        arena,
                        container.getTemporaryFile().pathString,
                        *createDecoderOptions(
                            sourceFormat = sourceFormat,
                            destinationFormat = destinationFormat,
                        ),
                    )

                transformation =
                    runBlocking {
                        transformationNormalizer.normalize(
                            requested = requestedTransformation,
                            originalVariantAttributes =
                                Attributes.createAttributes(
                                    image = image,
                                    sourceFormat = sourceFormat,
                                    destinationFormat = destinationFormat,
                                ),
                        )
                    }
            }
            checkNotNull(transformation)
        }
}
