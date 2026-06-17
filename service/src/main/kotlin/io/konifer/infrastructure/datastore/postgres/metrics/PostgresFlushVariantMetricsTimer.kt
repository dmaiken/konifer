package io.konifer.infrastructure.datastore.postgres.metrics

import io.konifer.infrastructure.variant.metrics.VariantMetricsDrainSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class PostgresFlushVariantMetricsTimer(
    scope: CoroutineScope,
    private val drainSignal: VariantMetricsDrainSignal,
    private val interval: Duration = 30.seconds,
) {
    init {
        scope.launch {
            while (isActive) {
                delay(interval)
                drainSignal.requestDrain()
            }
        }
    }
}
