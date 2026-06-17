package io.konifer.domain.ports

import io.konifer.domain.variant.VariantId
import java.time.Instant

interface VariantMetricsRepository {
    suspend fun recordVariantAccess(
        variantId: VariantId,
        path: String,
        accessedAt: Instant,
    )
}
