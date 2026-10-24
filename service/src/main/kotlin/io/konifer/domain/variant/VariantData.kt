package io.konifer.domain.variant

import io.konifer.domain.variant.attribute.Attributes
import java.time.Instant

data class VariantData(
    val id: VariantId,
    val objectStoreBucket: String,
    val objectStoreKey: String,
    val attributes: Attributes,
    val specification: VariantSpecification,
    val lqips: LQIPs,
    val createdAt: Instant,
    val uploadedAt: Instant?,
    val expiresAt: Instant?,
    val lastAccessedAt: Instant?,
) {
    val isOriginalVariant: Boolean
        get() = specification == VariantSpecification.Original
}
