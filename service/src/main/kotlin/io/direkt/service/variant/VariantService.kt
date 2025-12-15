package io.direkt.service.variant

import io.direkt.domain.asset.Asset
import io.direkt.domain.asset.AssetData
import io.direkt.domain.asset.AssetId
import io.direkt.domain.image.LQIPImplementation
import io.direkt.domain.ports.AssetRepository
import io.direkt.domain.ports.ObjectRepository
import io.direkt.domain.ports.TransformationDataContainer
import io.direkt.domain.ports.VariantGenerator
import io.direkt.domain.ports.VariantType
import io.direkt.domain.variant.Attributes
import io.direkt.domain.variant.Variant
import io.direkt.infrastructure.TemporaryFileFactory
import io.direkt.service.context.RequestedTransformation
import io.direkt.service.transformation.TransformationNormalizer
import io.ktor.util.logging.KtorSimpleLogger
import java.io.File
import java.util.UUID

class VariantService(
    private val assetRepository: AssetRepository,
    private val objectRepository: ObjectRepository,
    private val transformationNormalizer: TransformationNormalizer,
    private val variantGenerator: VariantGenerator,
) {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    suspend fun createEagerVariants(
        originalVariantFile: File,
        requestedTransformations: List<RequestedTransformation>,
        assetId: AssetId,
        originalVariantAttributes: Attributes,
        lqipImplementations: Set<LQIPImplementation>,
        bucket: String
    ) = generateVariants(
        originalVariantFile = originalVariantFile,
        requestedTransformations = requestedTransformations,
        assetId = assetId,
        originalVariantAttributes = originalVariantAttributes,
        lqipImplementations = lqipImplementations,
        bucket = bucket,
        variantType = VariantType.EAGER,
    )

    suspend fun generateOnDemandVariant(
        originalVariantFile: File,
        requestedTransformation: RequestedTransformation,
        assetId: AssetId,
        originalVariantAttributes: Attributes,
        lqipImplementations: Set<LQIPImplementation>,
        bucket: String
    ) = generateVariants(
        originalVariantFile = originalVariantFile,
        requestedTransformations = listOf(requestedTransformation),
        assetId = assetId,
        originalVariantAttributes = originalVariantAttributes,
        lqipImplementations = lqipImplementations,
        bucket = bucket,
        variantType = VariantType.ON_DEMAND,
    )

    private suspend fun generateVariants(
        originalVariantFile: File,
        requestedTransformations: List<RequestedTransformation>,
        assetId: AssetId,
        originalVariantAttributes: Attributes,
        lqipImplementations: Set<LQIPImplementation>,
        bucket: String,
        variantType: VariantType,
    ) {
        try {
            val transformationDataContainers = createTransformationDataContainers(
                originalVariantAttributes = originalVariantAttributes,
                requestedTransformations = requestedTransformations,
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
                            lqip = container.lqips
                        )
                    )
                    logger.info("Stored pending variant for ${container.transformation}: ${pendingVariant.id}")

                    val uploadedAt = objectRepository.persist(
                        bucket = pendingVariant.objectStoreBucket,
                        key = pendingVariant.objectStoreKey,
                        file = container.output
                    )

                    assetRepository.markUploaded(
                        variant = pendingVariant.markReady(uploadedAt)
                    )
                    logger.info("Variant ${pendingVariant.id} is ready and was uploaded to object store at: $uploadedAt")
                }
            } finally {
                transformationDataContainers.forEach {
                    it.output.delete()
                }
            }
        } finally {
            originalVariantFile.delete()
        }
    }

    private suspend fun createTransformationDataContainers(
        originalVariantAttributes: Attributes,
        requestedTransformations: List<RequestedTransformation>,
    ): List<TransformationDataContainer> = requestedTransformations.map { requestedTransformations ->
        val transformation = transformationNormalizer.normalize(
            requested = requestedTransformations,
            originalVariantAttributes = originalVariantAttributes
        )
        TransformationDataContainer(
            output = TemporaryFileFactory.createProcessedVariantTempFile(transformation.format.extension),
            transformation = transformation,
        )
    }

}