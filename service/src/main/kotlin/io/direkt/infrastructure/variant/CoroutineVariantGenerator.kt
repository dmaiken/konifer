package io.direkt.infrastructure.variant

import io.direkt.domain.asset.AssetData
import io.direkt.domain.image.LQIPImplementation
import io.direkt.domain.image.PreProcessedImage
import io.direkt.domain.ports.AssetRepository
import io.direkt.domain.ports.ObjectRepository
import io.direkt.domain.variant.Transformation
import io.direkt.domain.variant.Variant
import io.direkt.infrastructure.TemporaryFileFactory.createOriginalVariantTempFile
import io.direkt.infrastructure.vips.VipsImageProcessor
import io.direkt.service.transformation.TransformationNormalizer
import io.ktor.util.cio.writeChannel
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.copyAndClose
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

class CoroutineVariantGenerator(
    private val assetRepository: AssetRepository,
    private val objectStore: ObjectRepository,
    private val imageProcessor: VipsImageProcessor,
    private val consumer: PriorityChannelConsumer<ImageProcessingJob<*>>,
    private val transformationNormalizer: TransformationNormalizer,
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
                val job = consumer.nextJob()
                handleVariantGenerationJob(job)
            }
            logger.info("Shut down variant generator channel listener: $index")
        }
    }

    private suspend fun handleVariantGenerationJob(job: ImageProcessingJob<*>) {
        try {
            when (job) {
                is OnDemandVariantGenerationJob ->
                    handleVariantGenerationJob(job).also {
                        job.deferredResult.complete(it)
                    }
                is EagerVariantGenerationJob ->
                    handleEagerVariantGenerationJob(job).also {
                        job.deferredResult?.complete(it)
                    }
                is PreProcessJob ->
                    handlePreProcessJob(job).also {
                        job.deferredResult.complete(it)
                    }
            }
        } catch (e: Exception) {
            logger.error("Error while generating variant generation with request: {}", job, e)
            job.deferredResult?.completeExceptionally(e)
        }
    }

    private suspend fun handleVariantGenerationJob(job: OnDemandVariantGenerationJob): Variant {
        logger.info("Handling variant generation job: $job")
        val original =
            assetRepository.fetchByPath(job.path, job.entryId, Transformation.ORIGINAL_VARIANT)
                ?: throw IllegalStateException("No asset found for: ${job.path} and entryId: ${job.entryId}")
        return generateVariants(
            treePath = job.path,
            entryId = job.entryId,
            lqipImplementations = job.lqipImplementations,
            bucket = job.bucket,
            transformations = listOf(job.transformation),
            assetData = original,
        ).first()
    }

    private suspend fun handleEagerVariantGenerationJob(job: EagerVariantGenerationJob): List<Variant> {
        logger.info("Handling eager variant generation job: $job")
        val original =
            assetRepository.fetchByPath(job.path, job.entryId, Transformation.ORIGINAL_VARIANT)
                ?: throw IllegalStateException("No asset found for: ${job.path} and entryId: ${job.entryId}")
        return generateVariants(
            treePath = job.path,
            entryId = job.entryId,
            lqipImplementations = job.lqipImplementations,
            bucket = job.bucket,
            transformations =
                job.requestedTransformations.let {
                    transformationNormalizer.normalize(
                        requested = it,
                        originalVariantAttributes = original.variants.first().attributes,
                    )
                },
            assetData = original,
        )
    }

    private suspend fun handlePreProcessJob(job: PreProcessJob): PreProcessedImage {
        logger.info("Handling preprocessing job: $job")
        return imageProcessor.preprocess(
            sourceFormat = job.sourceFormat,
            transformation = job.transformation,
            lqipImplementations = job.lqipImplementations,
            source = job.source,
        )
    }

    private suspend fun generateVariants(
        treePath: String,
        entryId: Long?,
        lqipImplementations: Set<LQIPImplementation>,
        bucket: String,
        transformations: List<Transformation>,
        assetData: AssetData,
    ): List<Variant> =
        coroutineScope {
            if (transformations.isEmpty()) {
                logger.info("Got request to create variant for path: $treePath and entryId: $entryId but no specified variants")
                throw IllegalArgumentException("Job must contain requested image attributes")
            }

            val originalVariant = assetData.variants.first { it.isOriginalVariant }
            val found = objectStore.exists(originalVariant.objectStoreBucket, originalVariant.objectStoreKey)
            if (!found) {
                throw IllegalStateException(
                    "Cannot locate object with bucket: ${originalVariant.objectStoreBucket} key: ${originalVariant.objectStoreKey}",
                )
            }

            transformations.map { transformation ->
                val file = createOriginalVariantTempFile(extension = originalVariant.attributes.format.extension)
                try {
                    val originalVariantChannel = ByteChannel(true)
                    val fetchJob =
                        launch {
                            objectStore.fetch(
                                bucket = originalVariant.objectStoreBucket,
                                key = originalVariant.objectStoreKey,
                                stream = originalVariantChannel,
                            )
                        }
                    originalVariantChannel.copyAndClose(file.writeChannel())
                    fetchJob.join()
                    val processedImage =
                        imageProcessor.generateVariant(
                            source = file,
                            transformation = transformation,
                            originalVariant = originalVariant,
                            lqipImplementations = lqipImplementations,
                        )
                    val key = "${UUID.randomUUID()}${processedImage.attributes.format.extension}"
                    val variant = assetRepository.storeNewVariant(
                        variant = Variant.Pending.newVariant(
                            assetId = assetData.id,
                            attributes = processedImage.attributes,
                            transformation = processedImage.transformation,
                            objectStoreBucket = bucket,
                            objectStoreKey = key,
                            lqip = processedImage.lqip
                        )
                    )
                    val uploadedAt =
                        objectStore.persist(
                            bucket = bucket,
                            asset = processedImage.result,
                            key = key
                        )

                    variant.markReady(uploadedAt).also {
                        assetRepository.markUploaded(it)
                    }
                } finally {
                    file.delete()
                }
            }
        }
}
