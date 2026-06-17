package io.konifer.infrastructure.datastore.postgres.metrics

import io.konifer.infrastructure.variant.metrics.VariantMetricsDrainSignal
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class PostgresFlushVariantMetricsTimerTest {
    private val drainSignal = mockk<VariantMetricsDrainSignal>(relaxed = true)

    @Test
    fun `does not request a drain before the interval elapses`() =
        runTest {
            PostgresFlushVariantMetricsTimer(
                scope = backgroundScope,
                drainSignal = drainSignal,
                interval = 30.seconds,
            )

            advanceTimeBy(29.seconds)
            runCurrent()

            verify(exactly = 0) {
                drainSignal.requestDrain()
            }
        }

    @Test
    fun `requests a drain each time the interval elapses`() =
        runTest {
            PostgresFlushVariantMetricsTimer(
                scope = backgroundScope,
                drainSignal = drainSignal,
                interval = 30.seconds,
            )

            advanceTimeBy(30.seconds)
            runCurrent()

            verify(exactly = 1) {
                drainSignal.requestDrain()
            }

            advanceTimeBy(60.seconds)
            runCurrent()

            verify(exactly = 3) {
                drainSignal.requestDrain()
            }
        }
}
