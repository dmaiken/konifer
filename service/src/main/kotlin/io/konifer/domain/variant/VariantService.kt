package io.konifer.domain.variant

import com.github.f4b6a3.uuid.UuidCreator
import io.konifer.domain.asset.AssetId
import io.konifer.domain.path.PathConfiguration
import io.konifer.domain.ports.AssetRepository
import io.konifer.domain.ports.ObjectStore
import io.konifer.domain.ports.PersistObjectStoreRequest
import io.konifer.domain.ports.TransformationDataContainer
import io.konifer.domain.ports.VariantGenerator
import io.konifer.domain.ports.VariantType
import io.konifer.domain.transformation.RequestedTransformation
import io.konifer.domain.transformation.Transformation
import io.konifer.domain.transformation.TransformationNormalizer
import io.konifer.domain.transformation.TransformationValidator
import io.konifer.domain.variant.retention.RetentionProperties
import io.konifer.domain.variant.retention.VariantExpirationStrategy
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
        transformations.forEach { transformation ->
            TransformationValidator.validateNormalizedTransformation(
                transformProperties = pathConfiguration.transform,
                transformation = transformation,
            )
        }
        generateVariants(
            originalVariantFile = originalVariantFile,
            transformations = transformations,
            assetId = assetId,
            originalVariantLQIPs = originalVariantLQIPs,
            variantType = VariantType.EAGER,
            pathConfiguration = pathConfiguration,
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
            originalVariantLQIPs = originalVariantLQIPs,
            variantType = VariantType.ON_DEMAND,
            pathConfiguration = pathConfiguration,
        )
    }

    private suspend fun generateVariants(
        originalVariantFile: Path,
        transformations: List<Transformation>,
        assetId: AssetId,
        originalVariantLQIPs: LQIPs,
        variantType: VariantType,
        pathConfiguration: PathConfiguration,
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
                        lqipImplementations = pathConfiguration.lqip,
                        variantType = variantType,
                    )
            val expiresAt =
                calculateVariantExpiration(
                    retention = pathConfiguration.transform.retention,
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
                                    objectStoreBucket = pathConfiguration.objectStore.bucket,
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
                                cacheProperties = pathConfiguration.transform.retention.cache,
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

    private fun calculateVariantExpiration(
        retention: RetentionProperties,
        variantType: VariantType,
    ): LocalDateTime? =
        when (variantType) {
            VariantType.EAGER -> {
                if (retention.expire.strategy == VariantExpirationStrategy.TTL) {
                    retention.expire.expiresAt()
                } else {
                    // We don't want to expire if strategy is idle since the variant has not been accessed yet
                    null
                }
            }

            VariantType.ON_DEMAND -> {
                retention.expire.expiresAt()
            }
        }
}
