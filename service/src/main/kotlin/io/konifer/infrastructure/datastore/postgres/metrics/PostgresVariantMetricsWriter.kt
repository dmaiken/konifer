package io.konifer.infrastructure.datastore.postgres.metrics

import io.konifer.domain.variant.VariantId
import io.konifer.domain.variant.retention.VariantExpirationStrategy
import io.konifer.infrastructure.datastore.postgres.statement.FieldStatementGenerator.currentUtcLocalDateTime
import io.konifer.infrastructure.path.TriePathConfigurationRepository
import io.konifer.infrastructure.variant.metrics.InMemoryVariantMetricsRepository
import io.konifer.infrastructure.variant.metrics.VariantAccessedInformation
import io.konifer.infrastructure.variant.metrics.VariantMetricsDrainSignal
import io.ktor.util.logging.KtorSimpleLogger
import konifer.jooq.tables.references.ASSET_VARIANT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
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
        private const val ACCESS_COUNT = "access_count"
        private const val ACCESS_SCORE_HALF_LIFE_SECONDS = "access_score_half_life_seconds"
    }

    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    init {
        scope.launch {
            while (isActive) {
                drainSignal.awaitDrainRequest()
                flushMetrics()
            }
        }
    }

    private suspend fun flushMetrics() {
        val metricsToFlush =
            variantMetricsRepository
                .drainLastAccessed()
        logger.info("Flushing metrics for ${metricsToFlush.size} variants")
        metricsToFlush
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
            val accessScoreHalfLifeSeconds =
                pathConfig.transform.retention.cache.accessScoreHalfLife.inWholeMilliseconds / 1_000.0

            if (pathConfig.transform.retention.expire.strategy == VariantExpirationStrategy.IDLE) {
                idleUpdates +=
                    IdleMetricUpdate(
                        variantId = variantId.value,
                        accessedAt = accessedAtLocal,
                        expiresAt =
                            accessedAtLocal.plus(
                                checkNotNull(pathConfig.transform.retention.expire.ttl).toJavaDuration(),
                            ),
                        accessCount = accessed.accessCount,
                        accessScoreHalfLifeSeconds = accessScoreHalfLifeSeconds,
                    )
            } else {
                nonIdleUpdates +=
                    MetricUpdate(
                        variantId = variantId.value,
                        accessedAt = accessedAtLocal,
                        accessCount = accessed.accessCount,
                        accessScoreHalfLifeSeconds = accessScoreHalfLifeSeconds,
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
                            DSL.row(
                                update.variantId,
                                update.accessedAt,
                                update.accessCount,
                                update.accessScoreHalfLifeSeconds,
                            )
                        }.toTypedArray(),
                ).`as`(
                    INCOMING_VARIANT_METRICS,
                    ASSET_VARIANT.ID.name,
                    ASSET_VARIANT.LAST_ACCESSED_AT.name,
                    ACCESS_COUNT,
                    ACCESS_SCORE_HALF_LIFE_SECONDS,
                )

        val incomingId = checkNotNull(incoming.field(ASSET_VARIANT.ID.name, ASSET_VARIANT.ID.dataType))
        val incomingAccessedAt =
            checkNotNull(incoming.field(ASSET_VARIANT.LAST_ACCESSED_AT.name, ASSET_VARIANT.LAST_ACCESSED_AT.dataType))
        val incomingAccessCount = checkNotNull(incoming.field(ACCESS_COUNT, SQLDataType.BIGINT.nullable(false)))
        val incomingAccessScoreHalfLifeSeconds =
            checkNotNull(incoming.field(ACCESS_SCORE_HALF_LIFE_SECONDS, SQLDataType.DOUBLE.nullable(false)))
        val accessScoreAsOf = currentUtcLocalDateTime()

        dslContext
            .update(ASSET_VARIANT)
            .set(
                ASSET_VARIANT.LAST_ACCESSED_AT,
                DSL
                    .`when`(ASSET_VARIANT.LAST_ACCESSED_AT.isNull, incomingAccessedAt)
                    .otherwise(DSL.greatest(ASSET_VARIANT.LAST_ACCESSED_AT, incomingAccessedAt)),
            ).set(
                ASSET_VARIANT.ACCESS_SCORE,
                updatedAccessScore(
                    accessScoreAsOf = accessScoreAsOf,
                    incomingAccessCount = incomingAccessCount,
                    incomingAccessScoreHalfLifeSeconds = incomingAccessScoreHalfLifeSeconds,
                ),
            ).set(
                ASSET_VARIANT.ACCESS_SCORE_AS_OF,
                accessScoreAsOf,
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
                            DSL.row(
                                update.variantId,
                                update.accessedAt,
                                update.expiresAt,
                                update.accessCount,
                                update.accessScoreHalfLifeSeconds,
                            )
                        }.toTypedArray(),
                ).`as`(
                    INCOMING_IDLE_VARIANT_METRICS,
                    ASSET_VARIANT.ID.name,
                    ASSET_VARIANT.LAST_ACCESSED_AT.name,
                    ASSET_VARIANT.EXPIRES_AT.name,
                    ACCESS_COUNT,
                    ACCESS_SCORE_HALF_LIFE_SECONDS,
                )

        val incomingId = checkNotNull(incoming.field(ASSET_VARIANT.ID.name, ASSET_VARIANT.ID.dataType))
        val incomingAccessedAt =
            checkNotNull(incoming.field(ASSET_VARIANT.LAST_ACCESSED_AT.name, ASSET_VARIANT.LAST_ACCESSED_AT.dataType))
        val incomingExpiresAt = checkNotNull(incoming.field(ASSET_VARIANT.EXPIRES_AT.name, ASSET_VARIANT.EXPIRES_AT.dataType))
        val incomingAccessCount = checkNotNull(incoming.field(ACCESS_COUNT, SQLDataType.BIGINT.nullable(false)))
        val incomingAccessScoreHalfLifeSeconds =
            checkNotNull(incoming.field(ACCESS_SCORE_HALF_LIFE_SECONDS, SQLDataType.DOUBLE.nullable(false)))
        val shouldAdvanceAccessTime =
            ASSET_VARIANT.LAST_ACCESSED_AT.isNull.or(ASSET_VARIANT.LAST_ACCESSED_AT.lessThan(incomingAccessedAt))
        val accessScoreAsOf = currentUtcLocalDateTime()

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
            ).set(
                ASSET_VARIANT.ACCESS_SCORE,
                updatedAccessScore(
                    accessScoreAsOf = accessScoreAsOf,
                    incomingAccessCount = incomingAccessCount,
                    incomingAccessScoreHalfLifeSeconds = incomingAccessScoreHalfLifeSeconds,
                ),
            ).set(
                ASSET_VARIANT.ACCESS_SCORE_AS_OF,
                accessScoreAsOf,
            ).from(incoming)
            .where(ASSET_VARIANT.ID.eq(incomingId))
            .awaitFirstOrNull()
    }

    private fun updatedAccessScore(
        accessScoreAsOf: Field<LocalDateTime>,
        incomingAccessCount: Field<Long>,
        incomingAccessScoreHalfLifeSeconds: Field<Double>,
    ): Field<Double?> {
        val elapsedSeconds =
            DSL.field(
                "extract(epoch from ({0} - {1}))",
                SQLDataType.DOUBLE,
                accessScoreAsOf,
                ASSET_VARIANT.ACCESS_SCORE_AS_OF,
            )
        val decay =
            DSL
                .power(
                    0.5,
                    DSL.greatest(DSL.inline(0.0), elapsedSeconds).div(incomingAccessScoreHalfLifeSeconds),
                ).cast(Double::class.java)

        return ASSET_VARIANT.ACCESS_SCORE
            .mul(decay)
            .plus(incomingAccessCount.cast(Double::class.java))
    }

    private data class MetricUpdate(
        val variantId: UUID,
        val accessedAt: LocalDateTime,
        val accessCount: Long,
        val accessScoreHalfLifeSeconds: Double,
    )

    private data class IdleMetricUpdate(
        val variantId: UUID,
        val accessedAt: LocalDateTime,
        val expiresAt: LocalDateTime,
        val accessCount: Long,
        val accessScoreHalfLifeSeconds: Double,
    )
}
