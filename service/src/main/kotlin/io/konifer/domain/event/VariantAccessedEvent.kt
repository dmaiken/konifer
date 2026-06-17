package io.konifer.domain.event

import io.konifer.domain.variant.VariantId
import java.time.Instant

data class VariantAccessedEvent(
    val variantId: VariantId,
    val path: String,
    val accessedAt: Instant,
) : DomainEvent
