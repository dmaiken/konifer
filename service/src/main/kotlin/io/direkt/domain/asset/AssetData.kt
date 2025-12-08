package io.direkt.domain.asset

import io.direkt.domain.variant.VariantData
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
    val createdAt: LocalDateTime,
    val modifiedAt: LocalDateTime,
    val variants: List<VariantData>,
)
