package io.konifer.domain.asset

import io.konifer.application.usecase.fetch.CacheResult
import io.konifer.domain.context.QueryRequestContext
import io.konifer.domain.context.RequestContextValidator
import io.konifer.domain.event.VariantAccessedEvent
import io.konifer.domain.ports.AssetRepository
import io.konifer.domain.ports.EventPublisher
import io.konifer.domain.ports.ObjectStore
import io.konifer.domain.transformation.RequestedTransformation
import io.konifer.domain.transformation.Transformation
import io.konifer.domain.transformation.TransformationNormalizer
import io.konifer.domain.transformation.forDelivery
import io.konifer.domain.variant.VariantGenerationResult
import io.konifer.domain.variant.VariantId
import io.konifer.domain.variant.VariantSelection
import io.konifer.domain.variant.VariantService
import io.konifer.domain.variant.VariantSpecification
import io.konifer.domain.variant.projection.VariantDeliveryProjection
import io.konifer.infrastructure.TemporaryFileFactory
import io.ktor.util.cio.writeChannel
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.copyAndClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.io.path.deleteIfExists

class AssetService(
    private val assetRepository: AssetRepository,
    private val transformationNormalizer: TransformationNormalizer,
    private val formatValidator: FormatValidator,
    private val variantService: VariantService,
    private val objectStore: ObjectStore,
    private val eventPublisher: EventPublisher,
    private val requestContextValidator: RequestContextValidator,
) {
    suspend fun fetchForDelivery(context: QueryRequestContext): AssetDeliveryInformation? {
        val asset =
            assetRepository.fetchForDelivery(
                path = context.path,
                entryId = context.selectors.entryId,
                selection = if (context.requestedTransformation == null) VariantSelection.ORIGINAL_ONLY else VariantSelection.ALL,
                order = context.selectors.order,
                labels = context.labels,
            ) ?: return null

        val originalVariant =
            checkNotNull(asset.variants.firstOrNull { it.isOriginalVariant }) {
                "Original variant missing for ready asset: ${asset.path}:${asset.entryId}"
            }

        val specification =
            context.requestedTransformation?.let { requested ->
                val transformation =
                    transformationNormalizer.resolve(
                        requested = requested,
                        originalVariantAttributes = originalVariant.attributes,
                    )
                VariantSpecification.Transformed(transformation)
            } ?: VariantSpecification.Original

        val resolved =
            when (specification) {
                VariantSpecification.Original -> Transformation.fromAttributes(originalVariant.attributes)
                is VariantSpecification.Transformed -> specification.transformation
            }
        requestContextValidator.validateDelivery(context, originalVariant.attributes, resolved)
        val matched = asset.variants.findMatching(specification)

        val variant =
            matched ?: run {
                check(specification is VariantSpecification.Transformed) { "Cannot generate a missing original variant" }
                when (val generated = createOnDemandVariant(asset.id, originalVariant, specification.transformation, context)) {
                    is VariantGenerationResult.Generated -> {
                        generated.variant
                    }

                    VariantGenerationResult.AlreadyExists -> {
                        assetRepository.fetchVariantsForDelivery(asset.id).findMatching(specification) ?: return null
                    }
                }
            }

        return AssetDeliveryInformation(
            asset = asset,
            selectedVariant = variant,
            cacheResult = matched?.let { CacheResult.HIT } ?: CacheResult.MISS,
            deliveryTransformation = (context.requestedTransformation ?: RequestedTransformation()).forDelivery(resolved),
        ).also {
            publishVariantAccessedEvent(
                variantId = variant.id,
                path = asset.path,
            )
        }
    }

    private fun List<VariantDeliveryProjection>.findMatching(specification: VariantSpecification): VariantDeliveryProjection? =
        firstOrNull { variant ->
            when (specification) {
                VariantSpecification.Original -> variant.isOriginalVariant
                is VariantSpecification.Transformed -> specification.matches(stored = variant.specification) { variant.attributes }
            }
        }

    private suspend fun createOnDemandVariant(
        assetId: AssetId,
        originalVariant: VariantDeliveryProjection,
        transformation: Transformation,
        context: QueryRequestContext,
    ): VariantGenerationResult =
        coroutineScope {
            formatValidator.validateOutputFormat(
                allowedFormats = context.pathConfiguration.allowedFormats,
                format = transformation.format,
            )
            val originalVariantFile =
                TemporaryFileFactory.createOriginalVariantTempFile(
                    extension = originalVariant.attributes.format.extension,
                )
            try {
                val channel = ByteChannel()

                val fetchJob =
                    launch {
                        try {
                            objectStore.fetch(
                                bucket = originalVariant.objectStoreBucket,
                                key = originalVariant.objectStoreKey,
                                channel = channel,
                            )
                        } finally {
                            channel.close()
                        }
                    }
                channel.copyAndClose(originalVariantFile.toFile().writeChannel())
                fetchJob.join()
                variantService.generateOnDemandVariant(
                    originalVariantFile = originalVariantFile,
                    originalVariantAttributes = originalVariant.attributes,
                    transformation = transformation,
                    assetId = assetId,
                    originalVariantLQIPs = originalVariant.lqips,
                    pathConfiguration = context.pathConfiguration,
                )
            } finally {
                originalVariantFile.deleteIfExists()
            }
        }

    private suspend fun publishVariantAccessedEvent(
        variantId: VariantId,
        path: String,
    ) {
        eventPublisher.publish(
            VariantAccessedEvent(
                variantId = variantId,
                path = path,
                accessedAt = Instant.now(),
            ),
        )
    }
}
