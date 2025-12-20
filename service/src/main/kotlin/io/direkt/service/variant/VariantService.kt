package io.direkt.service.variant

import io.direkt.domain.asset.AssetId
import io.direkt.domain.image.LQIPImplementation
import io.direkt.domain.ports.AssetRepository
import io.direkt.domain.ports.ObjectRepository
import io.direkt.domain.ports.TransformationDataContainer
import io.direkt.domain.ports.VariantGenerator
import io.direkt.domain.ports.VariantType
import io.direkt.domain.variant.Attributes
import io.direkt.domain.variant.LQIPs
import io.direkt.domain.variant.Transformation
import io.direkt.domain.variant.Variant
import io.direkt.service.TemporaryFileFactory
import io.direkt.service.context.RequestedTransformation
import io.direkt.service.transformation.TransformationNormalizer
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Path
import java.util.UUID

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
        bucket: String
    ) {
        val transformations = transformationNormalizer.normalize(
            requested = requestedTransformations,
            originalVariantAttributes = originalVariantAttributes
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
        bucket: String
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
        val transformationDataContainers = createTransformationDataContainers(
            transformations = transformations,
        )
        try {
            variantGenerator.generateVariantsFromSource(
                source = originalVariantFile,
                transformationDataContainers = transformationDataContainers,
                lqipImplementations = lqipImplementations,
                variantType = variantType,
            ).await()
            logger.info("Eager variant content generated for ${transformationDataContainers.size} variants for asset: ${assetId.value}")

            for (container in transformationDataContainers) {
                val attributes = container.attributes ?: run {
                    logger.error("No attributes found for transformation ${container.transformation}, skipping variant persistence")
                    continue
                }
                val pendingVariant = assetRepository.storeNewVariant(
                    variant = Variant.Pending.newVariant(
                        assetId = assetId,
                        attributes = attributes,
                        transformation = container.transformation,
                        objectStoreBucket = bucket,
                        objectStoreKey = "${UUID.randomUUID()}${attributes.format.extension}",
                        lqip = if (lqipImplementations.isNotEmpty() && container.lqips == LQIPs.NONE) {
                            // No new Lqips were generated but they are required, use ones from the original variant
                            originalVariantLQIPs
                        } else {
                            container.lqips
                        }
                    )
                )
                logger.info("Stored pending variant for ${container.transformation}: ${pendingVariant.id}")

                val uploadedAt = objectRepository.persist(
                    bucket = pendingVariant.objectStoreBucket,
                    key = pendingVariant.objectStoreKey,
                    file = container.output.toFile()
                )

                assetRepository.markUploaded(
                    variant = pendingVariant.markReady(uploadedAt)
                )
                logger.info("Variant ${pendingVariant.id} is ready and was uploaded to object store at: $uploadedAt")
            }
        } finally {
            withContext(Dispatchers.IO) {
                transformationDataContainers.forEach {
                    it.output.toFile().delete()
                }
            }
        }
    }

    private suspend fun createTransformationDataContainers(
        transformations: List<Transformation>,
    ): List<TransformationDataContainer> = transformations.map { transformation ->
        TransformationDataContainer(
            output = TemporaryFileFactory.createProcessedVariantTempFile(transformation.format.extension),
            transformation = transformation,
        )
    }

}