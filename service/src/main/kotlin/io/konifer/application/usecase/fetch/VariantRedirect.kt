package io.konifer.application.usecase.fetch

import io.konifer.domain.asset.AssetData
import io.konifer.domain.variant.VariantData
import io.ktor.http.Url

data class VariantRedirect(
    val url: Url,
    val asset: AssetData,
    val variant: VariantData,
    val cacheHit: Boolean,
)
