package io.konifer.domain.variant

import io.konifer.domain.variant.attribute.Attributes
import java.time.LocalDateTime

data class VariantData(
    val id: VariantId,
    val objectStoreBucket: String,
    val objectStoreKey: String,
    val attributes: Attributes,
    val specification: VariantSpecification,
    val lqips: LQIPs,
    val createdAt: LocalDateTime,
    val uploadedAt: LocalDateTime?,
    val expiresAt: LocalDateTime?,
    val lastAccessedAt: LocalDateTime?,
) {
    val isOriginalVariant: Boolean
        get() = specification == VariantSpecification.Original
}
