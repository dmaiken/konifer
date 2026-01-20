package io.konifer.domain.asset

import io.konifer.domain.variant.VariantData
import java.time.LocalDateTime

data class AssetData(
    val id: AssetId,
    val path: String,
    val entryId: Long,
    val alt: String?,
    val labels: Map<String, String>,
    val tags: Set<String>,
    val source: AssetSource,
    val sourceUrl: String?,
    val isReady: Boolean,
    val createdAt: LocalDateTime,
    val modifiedAt: LocalDateTime,
    val variants: List<VariantData>,
)
