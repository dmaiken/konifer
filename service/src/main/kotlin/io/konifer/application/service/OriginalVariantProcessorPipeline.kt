package io.konifer.application.service

import io.konifer.domain.asset.AssetDataContainer
import io.konifer.domain.asset.AssetLabels
import io.konifer.domain.context.StoreRequestContext
import io.konifer.domain.ports.OriginalVariantContentProcessor
import io.konifer.domain.ports.TransformationDataContainer
import io.konifer.domain.rules.RuleDefinitionsEvaluationResult
import io.konifer.domain.rules.UploadRuleDecision
import io.konifer.domain.transformation.Transformation
import io.konifer.domain.transformation.TransformationNormalizer
import io.konifer.domain.transformation.TransformationValidator
import io.konifer.domain.variant.Attributes
import io.konifer.domain.variant.LQIPs
import io.konifer.domain.variant.ProcessingPipeline
import io.konifer.infrastructure.TemporaryFileFactory
import io.konifer.infrastructure.teeStream
import io.ktor.util.cio.readChannel
import io.ktor.util.cio.writeChannel
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.close
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlin.io.path.createLinkPointingTo

class OriginalVariantProcessorPipeline(
    private val transformationNormalizer: TransformationNormalizer,
    private val originalVariantContentProcessor: OriginalVariantContentProcessor,
) {
    suspend fun process(
        scope: CoroutineScope,
        container: AssetDataContainer,
        context: StoreRequestContext,
        attributes: Attributes,
    ): ProcessingPipeline {
        val hasEagerVariants =
            context.pathConfiguration.transform.eagerVariants
                .isNotEmpty()

        return if (context.requiresPreProcessing()) {
            buildPreProcessingPipeline(
                scope = scope,
                container = container,
                context = context,
                attributes = attributes,
                hasEagerVariants = hasEagerVariants,
            )
        } else {
            buildPassthroughPipeline(
                scope = scope,
                container = container,
                attributes = attributes,
                hasEagerVariants = hasEagerVariants,
            )
        }
    }

    private suspend fun buildPreProcessingPipeline(
        scope: CoroutineScope,
        container: AssetDataContainer,
        context: StoreRequestContext,
        attributes: Attributes,
        hasEagerVariants: Boolean,
    ): ProcessingPipeline {
        val transformation =
            normalizePreProcessing(
                context = context,
                attributes = attributes,
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
                TemporaryFileFactory.createPreProcessedTempFile(transformation.format.extension)
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
                        sourceFormat = attributes.format,
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
        attributes: Attributes,
        hasEagerVariants: Boolean,
    ): ProcessingPipeline {
        val objectStoreChannel = ByteChannel()
        val eagerVariantFile =
            if (hasEagerVariants) {
                TemporaryFileFactory.createPreProcessedTempFile(attributes.format.extension).apply {
                    // Hard reference - not a symbolic link!! This prevents the underlying file
                    // from being deleted when the container is closed
                    createLinkPointingTo(container.getTemporaryFile())
                }
            } else {
                null
            }

        // Since there is no preprocessing required here, we just stream the original file back to the caller
        val passthroughProcess: Deferred<UploadRuleDecision> =
            scope.async {
                runCatching {
                    teeStream(
                        source = container.getTemporaryFile().readChannel(),
                        firstChannel = objectStoreChannel,
                        secondChannel = null,
                    )
                    UploadRuleDecision.Success(
                        labels = AssetLabels.empty,
                        ruleDefinitionsEvaluationResult = RuleDefinitionsEvaluationResult.none,
                    )
                }.onFailure { e ->
                    objectStoreChannel.close(e)
                }.getOrThrow()
            }

        return ProcessingPipeline(
            attributes = CompletableDeferred(attributes),
            outputChannel = objectStoreChannel,
            eagerVariantFile = eagerVariantFile,
            processDeferred = passthroughProcess,
            lqips = CompletableDeferred(LQIPs.NONE),
        )
    }

    private suspend fun normalizePreProcessing(
        context: StoreRequestContext,
        attributes: Attributes,
    ): Transformation {
        val requestedTransformation =
            context.pathConfiguration.transform.preProcessing.image.requestedImageTransformation
        return transformationNormalizer
            .normalize(
                requested = requestedTransformation,
                originalVariantAttributes = attributes,
            ).also { normalized ->
                TransformationValidator.validateNormalizedTransformation(
                    transformProperties = context.pathConfiguration.transform,
                    transformation = normalized,
                )
            }
    }
}
