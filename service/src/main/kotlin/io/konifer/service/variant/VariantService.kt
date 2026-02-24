package io.konifer.service.variant

import com.github.f4b6a3.uuid.UuidCreator
import io.konifer.domain.asset.AssetId
import io.konifer.domain.image.LQIPImplementation
import io.konifer.domain.ports.AssetRepository
import io.konifer.domain.ports.ObjectStore
import io.konifer.domain.ports.TransformationDataContainerV2
import io.konifer.domain.ports.VariantAlreadyExistsException
import io.konifer.domain.ports.VariantGenerator
import io.konifer.domain.ports.VariantType
import io.konifer.domain.variant.Attributes
import io.konifer.domain.variant.LQIPs
import io.konifer.domain.variant.Transformation
import io.konifer.domain.variant.Variant
import io.konifer.service.context.RequestedTransformation
import io.konifer.service.transformation.TransformationNormalizer
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.nio.file.Path

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
    ): Unit =
        coroutineScope {
            val transformationDataContainers =
                createTransformationDataContainersV2(
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
                            )
                        // Start upload
                        val uploadJob =
                            async {
                                objectStore.persist(
                                    bucket = newVariant.objectStoreBucket,
                                    key = newVariant.objectStoreKey,
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
                }.joinAll()
            generationJob.await()
        }

    private fun createTransformationDataContainersV2(transformations: List<Transformation>): List<TransformationDataContainerV2> =
        transformations.map { transformation ->
            TransformationDataContainerV2(
                transformation = transformation,
            )
        }
}
