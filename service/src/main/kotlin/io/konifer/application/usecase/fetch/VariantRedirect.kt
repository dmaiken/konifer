package io.konifer.application.usecase.fetch

import io.konifer.domain.asset.projection.AssetDeliveryProjection
import io.konifer.domain.variant.projection.VariantDeliveryProjection

data class VariantRedirect(
    val deliveryUrl: DeliveryUrl,
    val asset: AssetDeliveryProjection,
    val variant: VariantDeliveryProjection,
    val cacheResult: CacheResult,
)
