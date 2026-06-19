package io.konifer.infrastructure.variant.metrics

import io.konifer.domain.ports.VariantMetricsRepository
import io.konifer.domain.variant.VariantId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

class InMemoryVariantMetricsRepository(
    private val drainSignal: VariantMetricsDrainSignal,
    private val maxEntries: Int = 10000,
) : VariantMetricsRepository {
    private val mutex = Mutex()
    private val lastAccessed = mutableMapOf<VariantId, VariantAccessedInformation>()

    override suspend fun recordVariantAccess(
        variantId: VariantId,
        path: String,
        accessedAt: Instant,
    ) {
        mutex.withLock {
            val accessedInformation = lastAccessed[variantId]
            lastAccessed[variantId] =
                if (accessedInformation == null) {
                    VariantAccessedInformation(
                        accessedAt = accessedAt,
                        path = path,
                    )
                } else {
                    VariantAccessedInformation(
                        accessedAt = maxOf(accessedInformation.accessedAt, accessedAt),
                        path = path,
                    )
                }

            if (lastAccessed.size > maxEntries) {
                drainSignal.requestDrain()
            }
        }
    }

    suspend fun drainLastAccessed(): Map<VariantId, VariantAccessedInformation> =
        mutex.withLock {
            val snapshot = lastAccessed.toMap()
            lastAccessed.clear()
            snapshot
        }
}

data class VariantAccessedInformation(
    val accessedAt: Instant,
    val path: String,
)
