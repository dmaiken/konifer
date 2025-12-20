package io.direkt.infrastructure.variant

import io.direkt.domain.image.PreProcessedImage
import io.direkt.domain.ports.AssetRepository
import io.direkt.domain.ports.ObjectRepository
import io.direkt.domain.variant.Transformation
import io.direkt.infrastructure.vips.VipsImageProcessor
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class CoroutineVariantGenerator(
    private val imageProcessor: VipsImageProcessor,
    private val consumer: PriorityChannelConsumer<ImageProcessingJob<*>>,
    numberOfWorkers: Int,
) {
    private val exceptionHandler =
        CoroutineExceptionHandler { _, exception ->
            logger.error("Variant generation failed", exception)
        }

    /**
     * Since these jobs will interact with vips-ffm, the dispatcher must be IO
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    init {
        logger.info("Starting $numberOfWorkers variant generator workers")
        repeat(numberOfWorkers) { index ->
            start(index)
        }
    }

    fun start(index: Int) {
        scope.launch {
            logger.info("Starting variant generator channel listener: $index")
            while (isActive) {
                handleVariantGenerationJob(consumer.nextJob())
            }
            logger.info("Shut down variant generator channel listener: $index")
        }
    }

    private suspend fun handleVariantGenerationJob(job: ImageProcessingJob<*>) {
        try {
            when (job) {
                is PreProcessJob ->
                    handlePreProcessJob(job).also {
                        job.deferredResult.complete(it)
                    }
                is GenerateVariantsJob -> handleGenerateVariantsJob(job).also {
                    job.deferredResult.complete(it)
                }
            }
        } catch (e: Exception) {
            logger.error("Error while generating variant generation with request: {}", job, e)
            job.deferredResult?.completeExceptionally(e)
        }
    }

    private suspend fun handlePreProcessJob(job: PreProcessJob): PreProcessedImage {
        logger.info("Handling preprocessing job: $job")
        return imageProcessor.preprocess(
            sourceFormat = job.sourceFormat,
            transformation = job.transformation,
            lqipImplementations = job.lqipImplementations,
            source = job.source,
            output = job.output,
        )
    }

    private suspend fun handleGenerateVariantsJob(job: GenerateVariantsJob): Boolean {
        logger.info("Handling GenerateVariantsJob job: $job")
        return try {
            imageProcessor.generateVariants(
                source = job.source,
                lqipImplementations = job.lqipImplementations,
                transformationDataContainers = job.transformationDataContainers,
            )
            true
        } catch (e: Exception) {
            logger.error("Error while generating variant with request: {}", job, e)
            false
        }
    }
}
