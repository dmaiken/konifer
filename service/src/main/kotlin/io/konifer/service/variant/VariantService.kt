package io.konifer.service.variant

import io.konifer.domain.asset.AssetId
import io.konifer.domain.image.LQIPImplementation
import io.konifer.domain.ports.AssetRepository
import io.konifer.domain.ports.ObjectRepository
import io.konifer.domain.ports.TransformationDataContainer
import io.konifer.domain.ports.VariantGenerator
import io.konifer.domain.ports.VariantType
import io.konifer.domain.variant.Attributes
import io.konifer.domain.variant.LQIPs
import io.konifer.domain.variant.Transformation
import io.konifer.domain.variant.Variant
import io.konifer.service.TemporaryFileFactory
import io.konifer.service.context.RequestedTransformation
import io.konifer.service.transformation.TransformationNormalizer
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.UUID
import kotlin.time.measureTime

class VariantService(
    private val assetRepository: AssetRepository,
    private val objectRepository: ObjectRepository,
    private val transformationNormalizer: TransformationNormalizer,
    private val variantGenerator: VariantGenerator,
) {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    suspend fun createEagerVariants(
        originalVariantFile: Path,
        requestedTransformations: List<RequestedTransformation>,
        assetId: AssetId,
        originalVariantAttributes: Attributes,
        lqipImplementations: Set<LQIPImplementation>,
        originalVariantLQIPs: LQIPs,
        bucket: String,
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
            lqipImplementations = lqipImplementations,
            originalVariantLQIPs = originalVariantLQIPs,
            bucket = bucket,
            variantType = VariantType.EAGER,
        )
    }

    suspend fun generateOnDemandVariant(
        originalVariantFile: Path,
        transformation: Transformation,
        assetId: AssetId,
        lqipImplementations: Set<LQIPImplementation>,
        originalVariantLQIPs: LQIPs,
        bucket: String,
    ) {
        generateVariants(
            originalVariantFile = originalVariantFile,
            transformations = listOf(transformation),
            assetId = assetId,
            lqipImplementations = lqipImplementations,
            originalVariantLQIPs = originalVariantLQIPs,
            bucket = bucket,
            variantType = VariantType.ON_DEMAND,
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
    ) {
        val transformationDataContainers =
            createTransformationDataContainers(
                transformations = transformations,
            )
        try {
            val time =
                measureTime {
                    variantGenerator
                        .generateVariantsFromSource(
                            source = originalVariantFile,
                            transformationDataContainers = transformationDataContainers,
                            lqipImplementations = lqipImplementations,
                            variantType = variantType,
                        ).await()
                }
            logger.info(
                "$variantType variant content generated for ${transformationDataContainers.size} variants for asset: ${assetId.value} in ${time.inWholeMilliseconds}ms",
            )

            for (container in transformationDataContainers) {
                val attributes =
                    container.attributes ?: run {
                        logger.error("No attributes found for transformation ${container.transformation}, skipping variant persistence")
                        continue
                    }
                val pendingVariant =
                    assetRepository.storeNewVariant(
                        variant =
                            Variant.Pending.newVariant(
                                assetId = assetId,
                                attributes = attributes,
                                transformation = container.transformation,
                                objectStoreBucket = bucket,
                                objectStoreKey = "${UUID.randomUUID()}${attributes.format.extension}",
                                lqip =
                                    if (lqipImplementations.isNotEmpty() && container.lqips == LQIPs.NONE) {
                                        // No new Lqips were generated but they are required, use ones from the original variant
                                        originalVariantLQIPs
                                    } else {
                                        container.lqips
                                    },
                            ),
                    )
                logger.info("Stored pending variant for ${container.transformation}: ${pendingVariant.id}")

                markVariantUploaded(
                    pendingVariant = pendingVariant,
                    container = container,
                ).also {
                    logger.info("Variant ${pendingVariant.id} is ready and was uploaded to object store at: $it")
                }
            }
        } finally {
            withContext(Dispatchers.IO) {
                transformationDataContainers.forEach {
                    it.output.toFile().delete()
                }
            }
        }
    }

    private suspend fun markVariantUploaded(
        pendingVariant: Variant.Pending,
        container: TransformationDataContainer,
    ): LocalDateTime {
        val uploadedAt =
            objectRepository.persist(
                bucket = pendingVariant.objectStoreBucket,
                key = pendingVariant.objectStoreKey,
                file = container.output.toFile(),
            )

        assetRepository.markUploaded(
            variant = pendingVariant.markReady(uploadedAt),
        )

        return uploadedAt
    }

    private suspend fun createTransformationDataContainers(transformations: List<Transformation>): List<TransformationDataContainer> =
        transformations.map { transformation ->
            TransformationDataContainer(
                output = TemporaryFileFactory.createProcessedVariantTempFile(transformation.format.extension),
                transformation = transformation,
            )
        }
}
