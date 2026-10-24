package io.konifer.asset.fetch

import io.konifer.common.selector.Order
import io.konifer.domain.asset.AssetId
import io.konifer.domain.asset.projection.AssetData
import io.konifer.domain.asset.projection.AssetDeliveryProjection
import io.konifer.domain.ports.AssetRepository
import io.konifer.domain.variant.Variant
import io.konifer.domain.variant.VariantData
import io.konifer.domain.variant.VariantSelection
import io.konifer.domain.variant.projection.VariantDeliveryProjection
import io.konifer.domain.variant.retention.CacheProperties
import java.util.concurrent.atomic.AtomicInteger

/** Counts metadata reads while preserving the real repository and generation workflow. */
internal class ObservedAssetRepository(
    private val delegate: AssetRepository,
) : AssetRepository by delegate {
    val assetReads = AtomicInteger()
    val assetForDeliveryReads = AtomicInteger()
    val collectionReads = AtomicInteger()
    val variantReads = AtomicInteger()
    var lastAsset: AssetData? = null
    var lastDeliveryAsset: AssetDeliveryProjection? = null
    var lastDeliverySelection: VariantSelection? = null
    var uploadedVariant: Variant.Ready? = null
    var uploadedMetadata: VariantData? = null
    var beforeVariantInsert: (suspend (Variant.Pending) -> Unit)? = null
    var afterAssetRead: (suspend () -> Unit)? = null
    var afterVariantInsert: (suspend () -> Unit)? = null
    var afterVariantUpload: (suspend () -> Unit)? = null

    fun resetReads() {
        assetReads.set(0)
        collectionReads.set(0)
        variantReads.set(0)
        assetForDeliveryReads.set(0)
        lastAsset = null
        lastDeliveryAsset = null
        lastDeliverySelection = null
        uploadedVariant = null
        uploadedMetadata = null
    }

    override suspend fun fetchByPath(
        path: String,
        entryId: Long?,
        selection: VariantSelection,
        order: Order,
        labels: Map<String, String>,
        includeOnlyReady: Boolean,
    ): AssetData? {
        assetReads.incrementAndGet()
        val asset = delegate.fetchByPath(path, entryId, selection, order, labels, includeOnlyReady)
        lastAsset = asset
        afterAssetRead?.invoke()
        return asset
    }

    override suspend fun fetchForDelivery(
        path: String,
        entryId: Long?,
        selection: VariantSelection,
        order: Order,
        labels: Map<String, String>,
    ): AssetDeliveryProjection? {
        assetForDeliveryReads.incrementAndGet()
        lastDeliverySelection = selection
        val asset = delegate.fetchForDelivery(path, entryId, selection, order, labels)
        lastDeliveryAsset = asset
        afterAssetRead?.invoke()
        return asset
    }

    override suspend fun fetchAllByPath(
        path: String,
        selection: VariantSelection,
        labels: Map<String, String>,
        order: Order,
        limit: Int,
    ): List<AssetData> {
        collectionReads.incrementAndGet()
        return delegate.fetchAllByPath(path, selection, labels, order, limit)
    }

    override suspend fun fetchVariantsForDelivery(
        assetId: AssetId,
        selection: VariantSelection,
    ): List<VariantDeliveryProjection> {
        variantReads.incrementAndGet()
        return delegate.fetchVariantsForDelivery(assetId, selection)
    }

    override suspend fun storeNewVariant(variant: Variant.Pending): Variant.Pending {
        beforeVariantInsert?.invoke(variant)
        val persisted = delegate.storeNewVariant(variant)
        afterVariantInsert?.invoke()
        return persisted
    }

    override suspend fun markUploaded(
        variant: Variant.Ready,
        cacheProperties: CacheProperties,
    ): VariantData {
        val persisted = delegate.markUploaded(variant, cacheProperties)
        uploadedVariant = variant
        uploadedMetadata = persisted
        afterVariantUpload?.invoke()
        return persisted
    }
}
