package io.konifer.application.usecase.fetch

import io.konifer.domain.asset.AssetData
import io.konifer.domain.variant.VariantData

data class VariantRedirect(
    val deliveryUrl: DeliveryUrl,
    val asset: AssetData,
    val variant: VariantData,
    val cacheHit: Boolean,
)
