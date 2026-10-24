package io.konifer.domain.asset

import io.konifer.application.usecase.fetch.CacheResult
import io.konifer.domain.asset.projection.AssetDeliveryProjection
import io.konifer.domain.transformation.RequestedTransformation
import io.konifer.domain.variant.projection.VariantDeliveryProjection

data class AssetDeliveryInformation(
    val asset: AssetDeliveryProjection,
    val selectedVariant: VariantDeliveryProjection,
    val cacheResult: CacheResult,
    val deliveryTransformation: RequestedTransformation,
)
