package io.konifer.infrastructure.datastore.postgres.metrics

import io.konifer.domain.variant.VariantExpirationStrategy
import io.konifer.domain.variant.VariantId
import io.konifer.infrastructure.path.TriePathConfigurationRepository
import io.konifer.infrastructure.variant.metrics.InMemoryVariantMetricsRepository
import io.konifer.infrastructure.variant.metrics.VariantAccessedInformation
import io.konifer.infrastructure.variant.metrics.VariantMetricsDrainSignal
import konifer.jooq.tables.references.ASSET_VARIANT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.kotlin.coroutines.transactionCoroutine
import java.time.LocalDateTime
import java.time.ZoneOffset.UTC
import java.util.UUID
import kotlin.time.toJavaDuration

class PostgresVariantMetricsWriter(
    scope: CoroutineScope,
    private val dslContext: DSLContext,
    private val drainSignal: VariantMetricsDrainSignal,
    private val variantMetricsRepository: InMemoryVariantMetricsRepository,
    private val pathConfigurationRepository: TriePathConfigurationRepository,
) {
    companion object {
        private const val CHUNK_SIZE = 500
        private const val INCOMING_VARIANT_METRICS = "incoming_variant_metrics"
        private const val INCOMING_IDLE_VARIANT_METRICS = "incoming_idle_variant_metrics"
    }

    init {
        scope.launch {
            while (isActive) {
                drainSignal.awaitDrainRequest()
                flushMetrics()
            }
        }
    }

    private suspend fun flushMetrics() {
        variantMetricsRepository
            .drainLastAccessed()
            .entries
            .chunked(CHUNK_SIZE)
            .forEach { chunk ->
                processChunk(chunk)
            }
    }

    private suspend fun processChunk(chunk: List<Map.Entry<VariantId, VariantAccessedInformation>>) {
        if (chunk.isEmpty()) return

        val idleUpdates = mutableListOf<IdleMetricUpdate>()
        val nonIdleUpdates = mutableListOf<MetricUpdate>()

        chunk.forEach { (variantId, accessed) ->
            val pathConfig = pathConfigurationRepository.fetch(accessed.path)
            val accessedAtLocal = LocalDateTime.ofInstant(accessed.accessedAt, UTC)

            if (pathConfig.transform.expire.strategy == VariantExpirationStrategy.IDLE) {
                idleUpdates +=
                    IdleMetricUpdate(
                        variantId = variantId.value,
                        accessedAt = accessedAtLocal,
                        expiresAt = accessedAtLocal.plus(checkNotNull(pathConfig.transform.expire.ttl).toJavaDuration()),
                    )
            } else {
                nonIdleUpdates +=
                    MetricUpdate(
                        variantId = variantId.value,
                        accessedAt = accessedAtLocal,
                    )
            }
        }

        dslContext.transactionCoroutine { trx ->
            updateLastAccessedAt(
                dslContext = trx.dsl(),
                updates = nonIdleUpdates,
            )
            updateLastAccessedAtAndExpiresAt(
                dslContext = trx.dsl(),
                updates = idleUpdates,
            )
        }
    }

    private suspend fun updateLastAccessedAt(
        dslContext: DSLContext,
        updates: List<MetricUpdate>,
    ) {
        if (updates.isEmpty()) return

        val incoming =
            DSL
                .values(
                    *updates
                        .map { update ->
                            DSL.row(update.variantId, update.accessedAt)
                        }.toTypedArray(),
                ).`as`(
                    INCOMING_VARIANT_METRICS,
                    ASSET_VARIANT.ID.name,
                    ASSET_VARIANT.LAST_ACCESSED_AT.name,
                )

        val incomingId = checkNotNull(incoming.field(ASSET_VARIANT.ID.name, ASSET_VARIANT.ID.dataType))
        val incomingAccessedAt =
            checkNotNull(incoming.field(ASSET_VARIANT.LAST_ACCESSED_AT.name, ASSET_VARIANT.LAST_ACCESSED_AT.dataType))

        dslContext
            .update(ASSET_VARIANT)
            .set(
                ASSET_VARIANT.LAST_ACCESSED_AT,
                DSL
                    .`when`(ASSET_VARIANT.LAST_ACCESSED_AT.isNull, incomingAccessedAt)
                    .otherwise(DSL.greatest(ASSET_VARIANT.LAST_ACCESSED_AT, incomingAccessedAt)),
            ).from(incoming)
            .where(ASSET_VARIANT.ID.eq(incomingId))
            .awaitFirstOrNull()
    }

    private suspend fun updateLastAccessedAtAndExpiresAt(
        dslContext: DSLContext,
        updates: List<IdleMetricUpdate>,
    ) {
        if (updates.isEmpty()) return

        val incoming =
            DSL
                .values(
                    *updates
                        .map { update ->
                            DSL.row(update.variantId, update.accessedAt, update.expiresAt)
                        }.toTypedArray(),
                ).`as`(
                    INCOMING_IDLE_VARIANT_METRICS,
                    ASSET_VARIANT.ID.name,
                    ASSET_VARIANT.LAST_ACCESSED_AT.name,
                    ASSET_VARIANT.EXPIRES_AT.name,
                )

        val incomingId = checkNotNull(incoming.field(ASSET_VARIANT.ID.name, ASSET_VARIANT.ID.dataType))
        val incomingAccessedAt =
            checkNotNull(incoming.field(ASSET_VARIANT.LAST_ACCESSED_AT.name, ASSET_VARIANT.LAST_ACCESSED_AT.dataType))
        val incomingExpiresAt = checkNotNull(incoming.field(ASSET_VARIANT.EXPIRES_AT.name, ASSET_VARIANT.EXPIRES_AT.dataType))
        val shouldAdvanceAccessTime =
            ASSET_VARIANT.LAST_ACCESSED_AT.isNull.or(ASSET_VARIANT.LAST_ACCESSED_AT.lessThan(incomingAccessedAt))

        dslContext
            .update(ASSET_VARIANT)
            .set(
                ASSET_VARIANT.LAST_ACCESSED_AT,
                DSL
                    .`when`(ASSET_VARIANT.LAST_ACCESSED_AT.isNull, incomingAccessedAt)
                    .otherwise(DSL.greatest(ASSET_VARIANT.LAST_ACCESSED_AT, incomingAccessedAt)),
            ).set(
                ASSET_VARIANT.EXPIRES_AT,
                DSL
                    .`when`(shouldAdvanceAccessTime, incomingExpiresAt)
                    .otherwise(ASSET_VARIANT.EXPIRES_AT),
            ).from(incoming)
            .where(ASSET_VARIANT.ID.eq(incomingId))
            .awaitFirstOrNull()
    }

    private data class MetricUpdate(
        val variantId: UUID,
        val accessedAt: LocalDateTime,
    )

    private data class IdleMetricUpdate(
        val variantId: UUID,
        val accessedAt: LocalDateTime,
        val expiresAt: LocalDateTime,
    )
}
