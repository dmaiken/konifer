package io.konifer.domain.variant

import com.github.f4b6a3.uuid.UuidCreator
import io.konifer.domain.asset.AssetId
import io.konifer.domain.context.RequestedTransformation
import io.konifer.domain.image.LQIPImplementation
import io.konifer.domain.path.PathConfiguration
import io.konifer.domain.ports.AssetRepository
import io.konifer.domain.ports.ObjectStore
import io.konifer.domain.ports.PersistObjectStoreRequest
import io.konifer.domain.ports.TransformationDataContainer
import io.konifer.domain.ports.VariantGenerator
import io.konifer.domain.ports.VariantType
import io.konifer.domain.transformation.Transformation
import io.konifer.domain.transformation.TransformationNormalizer
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.nio.file.Path
import java.time.LocalDateTime

class VariantService(
    private val assetRepository: AssetRepository,
    private val objectStore: ObjectStore,
    private val transformationNormalizer: TransformationNormalizer,
    private val variantGenerator: VariantGenerator,
) {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    suspend fun createEagerVariants(
        originalVariantFile: Path,
        requestedTransformations: List<RequestedTransformation>,
        assetId: AssetId,
        originalVariantAttributes: Attributes,
        originalVariantLQIPs: LQIPs,
        pathConfiguration: PathConfiguration,
    ) {
        val transformations =
            transformationNormalizer.normalize(
                requested = requestedTransformations,
                originalVariantAttributes = originalVariantAttributes,
            )
        generateVariants(
            originalVariantFile = originalVariantFile,
            transformations = transformations,
            assetId = assetId,
            lqipImplementations = pathConfiguration.image.previews,
            originalVariantLQIPs = originalVariantLQIPs,
            bucket = pathConfiguration.objectStore.bucket,
            variantType = VariantType.EAGER,
            expiresAt =
                if (pathConfiguration.transform.expire.strategy == VariantExpirationStrategy.TTL) {
                    pathConfiguration.transform.expire.expiresAt()
                } else {
                    // We don't want to expire if strategy is idle since the variant has not been accessed yet
                    null
                },
        )
    }

    suspend fun generateOnDemandVariant(
        originalVariantFile: Path,
        transformation: Transformation,
        assetId: AssetId,
        originalVariantLQIPs: LQIPs,
        pathConfiguration: PathConfiguration,
    ) {
        generateVariants(
            originalVariantFile = originalVariantFile,
            transformations = listOf(transformation),
            assetId = assetId,
            lqipImplementations = pathConfiguration.image.previews,
            originalVariantLQIPs = originalVariantLQIPs,
            bucket = pathConfiguration.objectStore.bucket,
            variantType = VariantType.ON_DEMAND,
            expiresAt = pathConfiguration.transform.expire.expiresAt(),
        )
    }

    private suspend fun generateVariants(
        originalVariantFile: Path,
        transformations: List<Transformation>,
        assetId: AssetId,
        lqipImplementations: Set<LQIPImplementation>,
        originalVariantLQIPs: LQIPs,
        bucket: String,
        variantType: VariantType,
        expiresAt: LocalDateTime?,
    ): Unit =
        coroutineScope {
            val transformationDataContainers =
                createTransformationDataContainers(
                    transformations = transformations,
                )
            val generationJob =
                variantGenerator
                    .generateVariantsFromSource(
                        source = originalVariantFile,
                        transformationDataContainers = transformationDataContainers,
                        lqipImplementations = lqipImplementations,
                        variantType = variantType,
                    )

            val variantGenerationJobs =
                transformationDataContainers
                    .map { container ->
                        launch {
                            val attributes = container.attributes.await()
                            val newVariant =
                                Variant.Pending.newVariant(
                                    assetId = assetId,
                                    attributes = attributes,
                                    transformation = container.transformation,
                                    objectStoreBucket = bucket,
                                    objectStoreKey = "${UuidCreator.getRandomBasedFast()}${attributes.format.extension}",
                                    lqip = container.lqips.await() ?: originalVariantLQIPs,
                                    expiresAt = expiresAt,
                                )
                            // Start upload
                            val uploadJob =
                                async {
                                    objectStore.persist(
                                        request =
                                            PersistObjectStoreRequest(
                                                bucket = newVariant.objectStoreBucket,
                                                key = newVariant.objectStoreKey,
                                                contentType = attributes.format,
                                            ),
                                        channel = container.output,
                                    )
                                }

                            val pendingVariant =
                                try {
                                    assetRepository.storeNewVariant(newVariant)
                                } catch (_: VariantAlreadyExistsException) {
                                    logger.info("Variant already exists for assetId: ${assetId.value}")
                                    uploadJob.cancel()
                                    return@launch
                                }

                            val uploadedAt = uploadJob.await()
                            assetRepository.markUploaded(
                                variant = pendingVariant.markReady(uploadJob.await()),
                            )
                            logger.info("Variant ${pendingVariant.id.value} is ready and was uploaded to object store at: $uploadedAt")
                        }
                    }
            generationJob.await()
            variantGenerationJobs.joinAll()
        }

    private fun createTransformationDataContainers(transformations: List<Transformation>): List<TransformationDataContainer> =
        transformations.map { transformation ->
            TransformationDataContainer(
                transformation = transformation,
            )
        }
}
