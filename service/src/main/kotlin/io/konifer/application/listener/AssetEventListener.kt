package io.konifer.application.listener

import io.konifer.domain.event.AssetReadyEvent
import io.konifer.domain.event.DomainEvent
import io.konifer.domain.ports.EventBus
import io.konifer.domain.ports.VariantProfileRepository
import io.konifer.domain.variant.VariantService
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.io.path.deleteIfExists
import kotlin.io.path.fileSize

class AssetEventListener(
    private val bus: EventBus,
    private val variantService: VariantService,
    private val variantProfileRepository: VariantProfileRepository,
    applicationScope: CoroutineScope,
) {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    init {
        applicationScope.launch {
            bus.events.collect { event ->
                launch {
                    runCatching {
                        handle(event)
                    }.onFailure { e ->
                        if (e is CancellationException) throw e
                        logger.error("Error handling asset event: ${event::class.simpleName}", e)
                    }
                }
            }
        }
    }

    private suspend fun handle(event: DomainEvent) {
        when (event) {
            is AssetReadyEvent -> handle(event)
        }
    }

    suspend fun handle(event: AssetReadyEvent) {
        try {
            val eagerVariantTransformations =
                event.pathConfiguration.eagerVariants
                    .map { profileName ->
                        variantProfileRepository.fetch(profileName)
                    }.takeIf { it.isNotEmpty() }
                    ?: return
            logger.info("Creating ${eagerVariantTransformations.size} eager variants for asset: ${event.originalVariant.assetId.value}")

            logger.info("TESTING: Eager variant file size is: ${event.originalVariantFile?.fileSize()}")
            variantService.createEagerVariants(
                originalVariantFile =
                    checkNotNull(event.originalVariantFile) {
                        "Eager variants defined but no original variant file supplied!"
                    },
                requestedTransformations = eagerVariantTransformations,
                assetId = event.originalVariant.assetId,
                originalVariantAttributes = event.originalVariant.attributes,
                lqipImplementations = event.pathConfiguration.image.previews,
                originalVariantLQIPs = event.originalVariant.lqips,
                bucket = event.pathConfiguration.objectStore.bucket,
            )
        } finally {
            logger.info("TESTING: deleting temp file")
            event.originalVariantFile?.deleteIfExists()
        }
    }
}
