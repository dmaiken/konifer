package io.konifer.domain.variant

import java.time.LocalDateTime

data class VariantData(
    val id: VariantId,
    val objectStoreBucket: String,
    val objectStoreKey: String,
    val isOriginalVariant: Boolean,
    val attributes: Attributes,
    val transformation: Transformation,
    val lqips: LQIPs,
    val createdAt: LocalDateTime,
    val uploadedAt: LocalDateTime?,
)
