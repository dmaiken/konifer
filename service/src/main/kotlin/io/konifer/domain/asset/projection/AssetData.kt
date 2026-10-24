package io.konifer.domain.asset.projection

import io.konifer.common.asset.AssetSource
import io.konifer.domain.asset.AssetId
import io.konifer.domain.variant.VariantData
import java.time.Instant

data class AssetData(
    val id: AssetId,
    val path: String,
    val entryId: Long,
    val alt: String?,
    val labels: Map<String, String>,
    val tags: Set<String>,
    val source: AssetSource,
    val externalSourceAddress: String?,
    val isReady: Boolean,
    val createdAt: Instant,
    val modifiedAt: Instant,
    val variants: List<VariantData>,
)
